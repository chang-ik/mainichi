package com.mainichi.api.domain.quiz.entity;

import java.util.List;

/**
 * 문제 하나 (quiz.questions 에 JSON 으로 저장).
 *
 * @param question    질문 (일본어, 후리가나 대괄호 표기)
 * @param choices     보기 4개
 * @param answerIndex 정답 보기 번호 (0~3)
 * @param explanation 해설 (한국어)
 */
public record QuizQuestion(String question, List<String> choices, int answerIndex, String explanation) {}
