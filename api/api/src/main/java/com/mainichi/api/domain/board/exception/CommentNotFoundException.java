package com.mainichi.api.domain.board.exception;

/** 없거나 삭제된 댓글 */
public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException() {
        super("COMMENT_NOT_FOUND");
    }
}
