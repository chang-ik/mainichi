package com.mainichi.api.domain.board.controller;

import com.mainichi.api.domain.board.controller.rqrs.CommentRequest;
import com.mainichi.api.domain.board.controller.rqrs.PostDetailResponse;
import com.mainichi.api.domain.board.controller.rqrs.PostPageResponse;
import com.mainichi.api.domain.board.controller.rqrs.PostRequest;
import com.mainichi.api.domain.board.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 학습 단계별 게시판. level = 0(기초) … 5(N5), 1 = N1 */
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /** 단계별 글 목록 (최신순, 한 페이지 20개) */
    @GetMapping("/{level}/posts")
    public ResponseEntity<PostPageResponse> list(@PathVariable int level, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(boardService.list(level, page));
    }

    @PostMapping("/{level}/posts")
    public ResponseEntity<PostDetailResponse> write(
            @AuthenticationPrincipal UUID userId, @PathVariable int level, @Valid @RequestBody PostRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(boardService.write(userId, level, request.title(), request.content()));
    }

    /** 글 상세와 댓글 */
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> get(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
        return ResponseEntity.ok(boardService.get(userId, postId));
    }

    @PatchMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> edit(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID postId, @Valid @RequestBody PostRequest request
    ) {
        return ResponseEntity.ok(boardService.edit(userId, postId, request.title(), request.content()));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
        boardService.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }

    /** 댓글을 달고 댓글이 반영된 글 상세를 받는다 */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<PostDetailResponse> comment(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID postId, @Valid @RequestBody CommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.comment(userId, postId, request.content()));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal UUID userId, @PathVariable UUID commentId) {
        boardService.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
