package com.mainichi.api.domain.quiz.entity;

/**
 * 문제를 만들 때 넘긴 오늘의 카드.
 *
 * @param front   앞면 (후리가나 대괄호 표기)
 * @param meaning 뜻 (한국어)
 * @param usage   문법 카드의 접속 형태. 단어 카드는 null
 * @param grammar 문법 카드인지
 */
public record QuizSourceCard(String front, String meaning, String usage, boolean grammar) {}
