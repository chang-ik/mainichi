package com.mainichi.api.domain.study.service;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.entity.JlptLevel;
import com.mainichi.api.domain.card.entity.WordBack;
import com.mainichi.api.domain.card.repository.CardRepository;
import com.mainichi.api.domain.card.service.WordBackCodec;
import com.mainichi.api.domain.study.StudyKind;
import com.mainichi.api.domain.study.controller.rqrs.StudyCardResponse;
import com.mainichi.api.domain.study.controller.rqrs.StudyQueueResponse;
import com.mainichi.api.domain.study.controller.rqrs.StudySettingsResponse;
import com.mainichi.api.domain.study.entity.CardState;
import com.mainichi.api.domain.study.entity.ReviewLog;
import com.mainichi.api.domain.study.entity.UserFsrsParam;
import com.mainichi.api.domain.study.exception.CardNotFoundException;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import com.mainichi.api.domain.study.fsrs.FsrsCard;
import com.mainichi.api.domain.study.fsrs.FsrsScheduler;
import com.mainichi.api.domain.study.fsrs.Rating;
import com.mainichi.api.domain.study.fsrs.ReviewOutcome;
import com.mainichi.api.domain.study.repository.CardStateRepository;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.study.repository.UserFsrsParamRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 단어·문법 카드 학습 (Anki 방식). 종류(StudyKind)마다 카드 큐와 하루 한도를 따로 센다.
 * 카드 순서: 학습 중 카드(기한 지남) → 오늘 복습 → 새 카드 → (할 게 없으면) 20분 안에 돌아올 학습 중 카드를 미리
 * 하루 기준은 유저 timezone 의 자정이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StudyService {

    /** 다른 카드가 없을 때 이 시간 안에 돌아올 학습 중 카드는 미리 보여준다 (Anki 의 learn ahead limit). */
    static final Duration LEARN_AHEAD = Duration.ofMinutes(20);

    private static final Set<CardPhase> LEARNING_PHASES = EnumSet.of(CardPhase.LEARNING, CardPhase.RELEARNING);
    private static final Set<CardPhase> REVIEW_PHASE = EnumSet.of(CardPhase.REVIEW);
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Seoul");

    private final UserService userService;
    private final CardRepository cardRepository;
    private final CardStateRepository cardStateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserFsrsParamRepository userFsrsParamRepository;
    private final Clock clock;

    public StudyQueueResponse getQueue(UUID userId, StudyKind kind) {
        User user = userService.getById(userId);
        return buildQueue(user, kind, paramsOf(user), LocalDateTime.now(clock));
    }

    /** 카드 한 장을 평가하고, 이어서 공부할 같은 종류의 다음 카드를 돌려준다. */
    public StudyQueueResponse review(UUID userId, StudyKind kind, UUID cardId, Rating rating) {
        User user = userService.getById(userId);
        Card card = cardRepository.findById(cardId)
                .filter(c -> kind.cardTag().equals(c.getCardTag()))
                .orElseThrow(CardNotFoundException::new);
        UserFsrsParam params = paramsOf(user);
        LocalDateTime now = LocalDateTime.now(clock);
        FsrsScheduler scheduler = new FsrsScheduler(params.toSettings());

        Optional<CardState> existing = cardStateRepository.findByUserIdAndCardId(userId, cardId);
        CardPhase before = existing.map(CardState::getState).orElse(CardPhase.NEW);
        FsrsCard current = existing.map(CardState::toFsrsCard).orElseGet(() -> FsrsCard.newCard(now));

        ReviewOutcome outcome = scheduler.review(current, rating, now);

        if (existing.isPresent()) {
            existing.get().apply(outcome, before);
        } else {
            cardStateRepository.save(CardState.first(user, card, outcome));
        }

        reviewLogRepository.save(ReviewLog.builder()
                .user(user)
                .card(card)
                .rating(rating.value())
                .state(before)
                .stability(outcome.card().stability())
                .difficulty(outcome.card().difficulty())
                .elapsedDays(outcome.elapsedDays())
                .scheduledDays(outcome.scheduledDays())
                .reviewTime(now)
                .build());

        return buildQueue(user, kind, params, now);
    }

    // ---------------------------------------------------------------- 설정

    public StudySettingsResponse getSettings(UUID userId) {
        return StudySettingsResponse.from(paramsOf(userService.getById(userId)));
    }

    /**
     * 하루 학습량을 바꾼다. 단어·문법에 각각 적용된다.
     * 오늘 이미 본 새 카드 수는 그대로 세므로, 줄이면 오늘 남은 새 카드도 바로 줄어든다.
     */
    public StudySettingsResponse updateSettings(UUID userId, int newCardsPerDay) {
        UserFsrsParam params = paramsOf(userService.getById(userId));
        params.changeNewCardsPerDay(newCardsPerDay);
        return StudySettingsResponse.from(params);
    }

    // ---------------------------------------------------------------- 큐 구성

    private StudyQueueResponse buildQueue(User user, StudyKind kind, UserFsrsParam params, LocalDateTime now) {
        UUID userId = user.getId();
        int level = JlptLevel.forStudy(user.getCurrentLevel());
        String tag = kind.cardTag();
        DayWindow today = todayOf(user, now);

        long introducedToday = reviewLogRepository.countSince(userId, tag, CardPhase.NEW, today.start());
        long reviewedToday = reviewLogRepository.countSince(userId, tag, CardPhase.REVIEW, today.start());

        long newCount = Math.min(
                Math.max(0, params.getNewCardsPerDay() - introducedToday),
                cardRepository.countUnseen(userId, tag, level));
        long reviewCount = Math.min(
                Math.max(0, params.getMaxReviewsPerDay() - reviewedToday),
                cardStateRepository.countDue(userId, tag, level, REVIEW_PHASE, today.lastMoment()));
        long learningCount = cardStateRepository.countDue(userId, tag, level, LEARNING_PHASES, now);

        FsrsScheduler scheduler = new FsrsScheduler(params.toSettings());
        StudyCardResponse next = null;

        if (learningCount > 0) {
            next = firstDue(userId, tag, level, LEARNING_PHASES, now, scheduler, now);
        } else if (reviewCount > 0) {
            next = firstDue(userId, tag, level, REVIEW_PHASE, today.lastMoment(), scheduler, now);
        } else if (newCount > 0) {
            next = cardRepository.findUnseen(userId, tag, level, PageRequest.of(0, 1)).stream()
                    .findFirst()
                    .map(c -> toResponse(c, FsrsCard.newCard(now), scheduler, now))
                    .orElse(null);
        } else {
            LocalDateTime aheadUntil = now.plus(LEARN_AHEAD);
            learningCount = cardStateRepository.countDue(userId, tag, level, LEARNING_PHASES, aheadUntil);
            if (learningCount > 0) {
                next = firstDue(userId, tag, level, LEARNING_PHASES, aheadUntil, scheduler, now);
            }
        }

        Long nextDueIn = null;
        if (next == null) {
            nextDueIn = cardStateRepository.findEarliestDue(userId, tag, level, LEARNING_PHASES)
                    .map(due -> Math.max(0, Duration.between(now, due).toSeconds()))
                    .orElse(null);
        }

        return new StudyQueueResponse(
                new StudyQueueResponse.Counts(newCount, learningCount, reviewCount), next, nextDueIn);
    }

    private StudyCardResponse firstDue(UUID userId, String tag, int level, Set<CardPhase> phases,
                                       LocalDateTime until, FsrsScheduler scheduler, LocalDateTime now) {
        return cardStateRepository.findDue(userId, tag, level, phases, until, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(s -> toResponse(s.getCard(), s.toFsrsCard(), scheduler, now))
                .orElse(null);
    }

    private StudyCardResponse toResponse(Card card, FsrsCard state, FsrsScheduler scheduler, LocalDateTime now) {
        WordBack back = WordBackCodec.decode(card.getBack());
        return new StudyCardResponse(
                card.getId().toString(),
                state.phase().name(),
                card.getFront(),
                back.meaning(),
                back.example(),
                back.exampleMeaning(),
                back.usage(),
                new StudyCardResponse.Intervals(
                        intervalSeconds(scheduler, state, Rating.AGAIN, now),
                        intervalSeconds(scheduler, state, Rating.HARD, now),
                        intervalSeconds(scheduler, state, Rating.GOOD, now),
                        intervalSeconds(scheduler, state, Rating.EASY, now)
                )
        );
    }

    private static long intervalSeconds(FsrsScheduler scheduler, FsrsCard state, Rating rating, LocalDateTime now) {
        return scheduler.review(state, rating, now).interval().toSeconds();
    }

    // ---------------------------------------------------------------- 보조

    private UserFsrsParam paramsOf(User user) {
        return userFsrsParamRepository.findByUserId(user.getId())
                .orElseGet(() -> userFsrsParamRepository.save(UserFsrsParam.defaultsFor(user)));
    }

    /** 유저 시간대 기준 "오늘"을 서버 시간(LocalDateTime) 범위로 바꾼다. */
    private DayWindow todayOf(User user, LocalDateTime now) {
        ZoneId userZone = zoneOf(user);
        ZoneId serverZone = clock.getZone();
        LocalDate today = now.atZone(serverZone).withZoneSameInstant(userZone).toLocalDate();
        LocalDateTime start = today.atStartOfDay(userZone).withZoneSameInstant(serverZone).toLocalDateTime();
        LocalDateTime end = today.plusDays(1).atStartOfDay(userZone).withZoneSameInstant(serverZone).toLocalDateTime();
        return new DayWindow(start, end);
    }

    private static ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return FALLBACK_ZONE;
        }
    }

    private record DayWindow(LocalDateTime start, LocalDateTime endExclusive) {
        LocalDateTime lastMoment() {
            return endExclusive.minusNanos(1);
        }
    }
}
