package com.mainichi.api.domain.quiz.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.mainichi.api.domain.quiz.entity.ListeningLine;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;

import java.util.List;

/** JLPT 형식 독해·청해 문제를 만든다. 실제 구현은 ClaudeQuizGenerator, 테스트에서는 가짜로 바꾼다. */
public interface QuizGenerator {

    /**
     * @param level 학습 단계 (0=기초, 1=N1 … 5=N5)
     * @param cards 지문에 녹여 쓸 오늘 새로 배운 카드
     * @throws com.mainichi.api.domain.quiz.exception.QuizGenerationException 만들지 못한 경우
     */
    GeneratedReading generateReading(int level, List<QuizSourceCard> cards);

    /**
     * @param level 학습 단계 (0=기초, 1=N1 … 5=N5)
     * @param cards 대화에 섞어 쓸 오늘 새로 배운 카드. 비어 있을 수 있다
     * @throws com.mainichi.api.domain.quiz.exception.QuizGenerationException 만들지 못한 경우
     */
    GeneratedListening generateListening(int level, List<QuizSourceCard> cards);

    // 모델이 돌려주는 형식 (structured output 스키마). 개수·범위 검증은 QuizService 가 한다.

    record GeneratedReading(
            @JsonPropertyDescription("지문 제목. 일본어, 후리가나 대괄호 표기")
            String title,
            @JsonPropertyDescription("지문. 일본어, 후리가나 대괄호 표기, 문단은 빈 줄로 구분")
            String passage,
            @JsonPropertyDescription("지문 전체의 자연스러운 한국어 해석")
            String translation,
            @JsonPropertyDescription("문제 정확히 2개")
            List<GeneratedQuestion> questions
    ) {}

    record GeneratedListening(
            @JsonPropertyDescription("화면에 보일 짧은 제목. 일본어, 후리가나 대괄호 표기")
            String title,
            @JsonPropertyDescription("대화 전에 읽어 주는 상황 설명 한 문장. 예: 男[おとこ]の人[ひと]と女[おんな]の人[ひと]が話[はな]しています。")
            String situation,
            @JsonPropertyDescription("대화. 남자(MALE)와 여자(FEMALE)가 번갈아 말한다")
            List<ListeningLine> lines,
            @JsonPropertyDescription("상황 설명과 대화 전체의 한국어 해석. 한 줄에 한 대사씩 '남: …', '여: …' 형식")
            String translation,
            @JsonPropertyDescription("문제 정확히 2개")
            List<GeneratedQuestion> questions
    ) {}

    record GeneratedQuestion(
            @JsonPropertyDescription("질문. 일본어, 후리가나 대괄호 표기")
            String question,
            @JsonPropertyDescription("보기 정확히 4개. 일본어, 후리가나 대괄호 표기, 번호는 붙이지 않는다")
            List<String> choices,
            @JsonPropertyDescription("정답 보기의 0부터 시작하는 번호 (0~3)")
            int answerIndex,
            @JsonPropertyDescription("정답인 이유와 지문/대화 속 근거를 한국어로 2~3문장")
            String explanation
    ) {}
}
