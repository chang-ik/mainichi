package com.mainichi.api.domain.board.exception;

/** 작성자가 아닌 사람이 글·댓글을 고치거나 지우려는 경우 */
public class NotAuthorException extends RuntimeException {
    public NotAuthorException() {
        super("NOT_AUTHOR");
    }
}
