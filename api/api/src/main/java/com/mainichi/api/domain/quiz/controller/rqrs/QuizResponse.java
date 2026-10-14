package com.mainichi.api.domain.quiz.controller.rqrs;

import com.mainichi.api.domain.quiz.QuizKind;
import com.mainichi.api.domain.quiz.entity.ListeningLine;
import com.mainichi.api.domain.quiz.entity.Quiz;
import com.mainichi.api.domain.quiz.entity.QuizQuestion;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.service.QuizCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 문제 한 세트. 풀기 전에는 정답·해설·해석을 보내지 않는다(null).
 * 청해 대화(script)는 브라우저가 소리로 읽어야 하므로 풀기 전에도 보낸다 (화면에는 풀고 나서 보여준다).
 *
 * @param passage      독해 지문 / 청해 상황 설명
 * @param script       청해 대화. 독해는 빈 목록
 * @param correctCount 맞힌 개수. 풀기 전에는 null
 */
public record QuizResponse(
        String id,
        QuizKind kind,
        int level,
        String title,
        String passage,
        List<ListeningLine> script,
        String translation,
        List<QuizSourceCard> sourceCards,
        List<Question> questions,
        boolean submitted,
        Integer correctCount
) {

    /**
     * @param selectedIndex 고른 보기 (풀기 전 null)
     * @param answerIndex   정답 보기 (풀기 전 null)
     * @param explanation   해설 (풀기 전 null)
     */
    public record Question(String question, List<String> choices, Integer selectedIndex,
                           Integer answerIndex, String explanation) {}

    public static QuizResponse from(Quiz quiz) {
        boolean submitted = quiz.isSubmitted();
        List<QuizQuestion> questions = QuizCodec.questions(quiz.getQuestions());
        List<Integer> answers = QuizCodec.answers(quiz.getSelectedAnswers());

        List<Question> items = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion q = questions.get(i);
            if (!submitted) {
                items.add(new Question(q.question(), q.choices(), null, null, null));
                continue;
            }
            Integer selected = i < answers.size() ? answers.get(i) : null;
            if (selected != null && selected == q.answerIndex()) correct++;
            items.add(new Question(q.question(), q.choices(), selected, q.answerIndex(), q.explanation()));
        }

        return new QuizResponse(
                quiz.getId().toString(),
                quiz.getKind(),
                quiz.getLevel(),
                quiz.getTitle(),
                quiz.getPassage(),
                QuizCodec.script(quiz.getScript()),
                submitted ? quiz.getTranslation() : null,
                QuizCodec.sourceCards(quiz.getSourceCards()),
                items,
                submitted,
                submitted ? correct : null
        );
    }
}
