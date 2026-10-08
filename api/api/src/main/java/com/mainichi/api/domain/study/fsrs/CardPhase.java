package com.mainichi.api.domain.study.fsrs;

/**
 * 카드 학습 단계.
 * NEW 는 card_state 행이 아직 없는 카드를 뜻하고, 한 번이라도 복습하면 나머지 셋 중 하나가 된다.
 */
public enum CardPhase {
    NEW, LEARNING, REVIEW, RELEARNING
}
