package com.mainichi.api.domain.board.controller.rqrs;

import com.mainichi.api.domain.board.entity.Comment;
import com.mainichi.api.domain.board.entity.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 글 상세와 댓글.
 *
 * @param edited 처음 쓴 뒤 고쳤으면 true
 * @param mine   내가 쓴 글이면 true (수정·삭제 버튼 표시용)
 */
public record PostDetailResponse(
        UUID id,
        int level,
        String title,
        String content,
        String author,
        LocalDateTime createdAt,
        boolean edited,
        boolean mine,
        List<CommentResponse> comments
) {
    public static PostDetailResponse of(Post p, List<Comment> comments, UUID viewerId) {
        boolean edited = p.getUpdatedAt() != null && p.getCreatedAt() != null
                && p.getUpdatedAt().isAfter(p.getCreatedAt().plusSeconds(1));
        return new PostDetailResponse(p.getId(), p.getLevel(), p.getTitle(), p.getContent(), p.getUser().getNickname(),
                p.getCreatedAt(), edited, p.isWrittenBy(viewerId),
                comments.stream().map(c -> CommentResponse.of(c, viewerId)).toList());
    }
}
