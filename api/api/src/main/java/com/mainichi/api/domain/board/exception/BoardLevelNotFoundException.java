package com.mainichi.api.domain.board.exception;

/** /board/{level} 의 단계가 0~5 가 아닌 경우 */
public class BoardLevelNotFoundException extends RuntimeException {
    public BoardLevelNotFoundException() {
        super("BOARD_LEVEL_NOT_FOUND");
    }
}
