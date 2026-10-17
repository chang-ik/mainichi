package com.mainichi.api.domain.quiz.service;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.entity.JlptLevel;
import com.mainichi.api.domain.card.entity.WordBack;
import com.mainichi.api.domain.card.service.WordBackCodec;
import com.mainichi.api.domain.quiz.QuizKind;
import com.mainichi.api.domain.quiz.controller.rqrs.QuizResponse;
import com.mainichi.api.domain.quiz.controller.rqrs.TodayQuizResponse;
import com.mainichi.api.domain.quiz.entity.ListeningLine;
import com.mainichi.api.domain.quiz.entity.Quiz;
import com.mainichi.api.domain.quiz.entity.QuizQuestion;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.exception.NotEnoughNewCardsException;
import com.mainichi.api.domain.quiz.exception.QuizDailyLimitException;
import com.mainichi.api.domain.quiz.exception.QuizGenerationException;
import com.mainichi.api.domain.quiz.exception.QuizNotFoundException;
import com.mainichi.api.domain.quiz.repository.QuizRepository;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedListening;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedQuestion;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedReading;
import com.mainichi.api.domain.study.StudyDay;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Claude 로 JLPT 독해·청해 문제(지문/대화 1개 + 문제 2개)를 만들고 채점한다.
 * 독해는 오늘 새로 배운 카드로 지문을 쓰고, 청해는 단계에 맞는 대화에 오늘 배운 카드를 섞는다.
 * 문제 생성은 Claude 응답을 기다리는 동안 DB 커넥션을 잡지 않도록 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    /** 문제에 넘길 카드 수 상한 (최근에 배운 카드부터) */
    static final int MAX_SOURCE_CARDS = 30;
    /** 종류별로 하루에 만들 수 있는 문제 수 */
    static final int DAILY_LIMIT = 3;
    static final int QUESTION_COUNT = 2;
    static final int CHOICE_COUNT = 4;
    static final int MIN_LINES = 2;
    static final int MAX_LINES = 30;
    /** 모델 응답이 형식에 안 맞거나 일시적으로 실패하면 이만큼 다시 시도한다 */
    static final int MAX_ATTEMPTS = 2;

    private final UserService userService;
    private final ReviewLogRepository reviewLogRepository;
    private final QuizRepository quizRepository;
    private final QuizGenerator quizGenerator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayQuizResponse getToday(UUID userId, QuizKind kind) {
        User user = userService.getById(userId);
        StudyDay today = StudyDay.of(user, LocalDateTime.now(clock), clock.getZone());

        long newCards = reviewLogRepository.countAllSince(userId, CardPhase.NEW, today.start());
        long made = quizRepository.countByUserIdAndKindAndCreatedTimeGreaterThanEqual(userId, kind, today.start());
        long solved = quizRepository.countByUserIdAndKindAndSubmittedTimeGreaterThanEqual(userId, kind, today.start());
        QuizResponse latest = latestOf(userId, kind, today).map(QuizResponse::from).orElse(null);
        return new TodayQuizResponse(newCards, kind.minNewCards(), Math.max(0, DAILY_LIMIT - made), solved, latest);
    }

    /** 새 문제를 만든다. 오늘 만든 같은 종류의 문제를 아직 안 풀었으면 새로 만들지 않고 그 문제를 돌려준다. */
    public QuizResponse create(UUID userId, QuizKind kind) {
        User user = userService.getById(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        StudyDay today = StudyDay.of(user, now, clock.getZone());

        Optional<Quiz> latest = latestOf(userId, kind, today);
        if (latest.isPresent() && !latest.get().isSubmitted()) {
            return QuizResponse.from(latest.get());
        }
        if (quizRepository.countByUserIdAndKindAndCreatedTimeGreaterThanEqual(userId, kind, today.start()) >= DAILY_LIMIT) {
            throw new QuizDailyLimitException();
        }

        List<Card> cards = reviewLogRepository.findCardsSince(
                userId, CardPhase.NEW, today.start(), PageRequest.of(0, MAX_SOURCE_CARDS));
        if (cards.size() < kind.minNewCards()) {
            throw new NotEnoughNewCardsException();
        }

        int level = JlptLevel.forStudy(user.getCurrentLevel());
        List<QuizSourceCard> sources = cards.stream().map(QuizService::toSource).toList();
        Draft draft = generate(kind, level, sources);

        Quiz quiz = quizRepository.save(Quiz.builder()
                .user(user)
                .kind(kind)
                .level(level)
                .title(draft.title())
                .passage(draft.passage())
                .script(draft.script() == null ? null : QuizCodec.encode(draft.script()))
                .translation(draft.translation())
                .questions(QuizCodec.encode(draft.questions()))
                .sourceCards(QuizCodec.encode(sources))
                .createdTime(now)
                .build());
        return QuizResponse.from(quiz);
    }

    /** 답을 채점한다. 이미 푼 문제면 처음 결과를 그대로 돌려준다. */
    @Transactional
    public QuizResponse submit(UUID userId, QuizKind kind, UUID quizId, List<Integer> answers) {
        Quiz quiz = quizRepository.findByIdAndUserIdAndKind(quizId, userId, kind)
                .orElseThrow(QuizNotFoundException::new);
        if (!quiz.isSubmitted()) {
            quiz.submit(QuizCodec.encode(answers), LocalDateTime.now(clock));
        }
        return QuizResponse.from(quiz);
    }

    // ---------------------------------------------------------------- 보조

    /** 모델 응답을 받아 검증한다. 실패(서버 오류·형식 오류)는 한 번 더 시도한다. 한도·키 없음은 바로 넘긴다. */
    private Draft generate(QuizKind kind, int level, List<QuizSourceCard> sources) {
        for (int attempt = 1; ; attempt++) {
            try {
                return switch (kind) {
                    case READING -> Draft.of(quizGenerator.generateReading(level, sources));
                    case LISTENING -> Draft.of(quizGenerator.generateListening(level, sources));
                };
            } catch (QuizGenerationException e) {
                if (attempt >= MAX_ATTEMPTS) throw e;
                log.info("{} 문제 생성 실패, 다시 시도 ({}/{})", kind, attempt + 1, MAX_ATTEMPTS);
            }
        }
    }

    private Optional<Quiz> latestOf(UUID userId, QuizKind kind, StudyDay today) {
        return quizRepository.findFirstByUserIdAndKindAndCreatedTimeGreaterThanEqualOrderByCreatedTimeDesc(
                userId, kind, today.start());
    }

    private static QuizSourceCard toSource(Card card) {
        WordBack back = WordBackCodec.decode(card.getBack());
        return new QuizSourceCard(card.getFront(), back.meaning(), back.usage(),
                Card.TAG_GRAMMAR.equals(card.getCardTag()));
    }

    /** 검증을 통과한 모델 응답. 화면에 그릴 수 있는 형태인지(문제 2개, 보기 4개, 정답 0~3, 대사 수) 확인한다. */
    record Draft(String title, String passage, List<ListeningLine> script, String translation,
                 List<QuizQuestion> questions) {

        static Draft of(GeneratedReading r) {
            if (r == null) throw invalid("응답이 비어 있음");
            return new Draft(text("title", r.title()), text("passage", r.passage()), null,
                    text("translation", r.translation()), questions(r.questions()));
        }

        static Draft of(GeneratedListening l) {
            if (l == null) throw invalid("응답이 비어 있음");
            if (l.lines() == null || l.lines().size() < MIN_LINES || l.lines().size() > MAX_LINES) {
                throw invalid("대사 수 " + (l.lines() == null ? null : l.lines().size()));
            }
            if (l.lines().stream().anyMatch(line -> line == null || line.speaker() == null || isBlank(line.text()))) {
                throw invalid("화자나 대사가 빈 줄이 있음");
            }
            List<ListeningLine> script = l.lines().stream()
                    .map(line -> new ListeningLine(line.speaker(), line.text().strip()))
                    .toList();
            return new Draft(text("title", l.title()), text("situation", l.situation()), script,
                    text("translation", l.translation()), questions(l.questions()));
        }

        private static List<QuizQuestion> questions(List<GeneratedQuestion> questions) {
            if (questions == null || questions.size() != QUESTION_COUNT) {
                throw invalid("문제 수 " + (questions == null ? null : questions.size()));
            }
            return questions.stream().map(Draft::question).toList();
        }

        private static QuizQuestion question(GeneratedQuestion q) {
            if (q == null) throw invalid("문제가 비어 있음");
            if (q.choices() == null || q.choices().size() != CHOICE_COUNT
                    || q.choices().stream().anyMatch(QuizService::isBlank)) {
                throw invalid("보기 " + q.choices());
            }
            if (q.answerIndex() < 0 || q.answerIndex() >= CHOICE_COUNT) {
                throw invalid("정답 번호 " + q.answerIndex());
            }
            return new QuizQuestion(text("question", q.question()), q.choices().stream().map(String::strip).toList(),
                    q.answerIndex(), text("explanation", q.explanation()));
        }

        private static String text(String field, String s) {
            if (isBlank(s)) throw invalid(field + " 가 비어 있음");
            return s.strip();
        }

        private static QuizGenerationException invalid(String reason) {
            log.warn("모델 응답이 문제 형식에 맞지 않음: {}", reason);
            return new QuizGenerationException();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
