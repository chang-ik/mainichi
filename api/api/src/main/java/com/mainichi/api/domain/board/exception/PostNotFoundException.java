package com.mainichi.api.domain.board.exception;

/** 없거나 삭제된 글 */
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException() {
        super("POST_NOT_FOUND");
    }
}
