package com.mainichi.api.domain.quiz.controller.rqrs;

/**
 * 오늘의 독해·청해 상태.
 *
 * @param newCardCount   오늘 새로 배운 카드 수 (단어+문법)
 * @param minNewCards    문제를 만들 수 있는 최소 카드 수 (청해는 0)
 * @param remainingToday 오늘 더 만들 수 있는 문제 수
 * @param solvedToday    오늘 채점까지 끝낸 문제 수 (1 이상이면 학습 선택 화면에서 "완료")
 * @param quiz           오늘 가장 최근에 만든 문제. 없으면 null
 */
public record TodayQuizResponse(
        long newCardCount,
        int minNewCards,
        long remainingToday,
        long solvedToday,
        QuizResponse quiz
) {}
