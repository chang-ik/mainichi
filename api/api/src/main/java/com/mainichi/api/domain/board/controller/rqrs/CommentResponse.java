package com.mainichi.api.domain.board.controller.rqrs;

import com.mainichi.api.domain.board.entity.Comment;

import java.time.LocalDateTime;
import java.util.UUID;

/** @param mine 내가 쓴 댓글이면 true (삭제 버튼 표시용) */
public record CommentResponse(UUID id, String content, String author, LocalDateTime createdAt, boolean mine) {

    public static CommentResponse of(Comment c, UUID viewerId) {
        return new CommentResponse(c.getId(), c.getContent(), c.getUser().getNickname(), c.getCreatedAt(),
                c.isWrittenBy(viewerId));
    }
}
