package com.mainichi.api.domain.board.service;

import com.mainichi.api.domain.board.controller.rqrs.PostDetailResponse;
import com.mainichi.api.domain.board.controller.rqrs.PostPageResponse;
import com.mainichi.api.domain.board.controller.rqrs.PostSummary;
import com.mainichi.api.domain.board.entity.Comment;
import com.mainichi.api.domain.board.entity.Post;
import com.mainichi.api.domain.board.exception.BoardLevelNotFoundException;
import com.mainichi.api.domain.board.exception.CommentNotFoundException;
import com.mainichi.api.domain.board.exception.NotAuthorException;
import com.mainichi.api.domain.board.exception.PostNotFoundException;
import com.mainichi.api.domain.board.repository.CommentRepository;
import com.mainichi.api.domain.board.repository.PostRepository;
import com.mainichi.api.domain.card.entity.JlptLevel;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 학습 단계별 게시판 (0=기초, 1=N1 … 5=N5). 어느 단계 게시판이든 읽고 쓸 수 있고,
 * 글·댓글은 쓴 사람만 고치거나 지울 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    static final int PAGE_SIZE = 20;

    private final UserService userService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public PostPageResponse list(int level, int page) {
        checkLevel(level);
        Page<PostSummary> result = postRepository.findSummaries(level, PageRequest.of(Math.max(0, page), PAGE_SIZE));
        return new PostPageResponse(level, result.getContent(), result.getNumber(), result.getTotalPages(),
                result.getTotalElements());
    }

    public PostDetailResponse write(UUID userId, int level, String title, String content) {
        checkLevel(level);
        User user = userService.getById(userId);
        Post post = postRepository.save(Post.builder()
                .user(user).level(level).title(title.strip()).content(content.strip()).build());
        return PostDetailResponse.of(post, List.of(), userId);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse get(UUID userId, UUID postId) {
        Post post = postOf(postId);
        return PostDetailResponse.of(post, commentRepository.findByPostWithUser(post), userId);
    }

    public PostDetailResponse edit(UUID userId, UUID postId, String title, String content) {
        Post post = mine(userId, postOf(postId));
        post.edit(title.strip(), content.strip());
        postRepository.flush();
        return PostDetailResponse.of(post, commentRepository.findByPostWithUser(post), userId);
    }

    public void delete(UUID userId, UUID postId) {
        Post post = mine(userId, postOf(postId));
        commentRepository.deleteAllByPost(post);
        postRepository.delete(post);
    }

    public PostDetailResponse comment(UUID userId, UUID postId, String content) {
        Post post = postOf(postId);
        commentRepository.save(Comment.builder()
                .post(post).user(userService.getById(userId)).content(content.strip()).build());
        return PostDetailResponse.of(post, commentRepository.findByPostWithUser(post), userId);
    }

    public void deleteComment(UUID userId, UUID commentId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(CommentNotFoundException::new);
        if (!comment.isWrittenBy(userId)) throw new NotAuthorException();
        commentRepository.delete(comment);
    }

    // ---------------------------------------------------------------- 보조

    private Post postOf(UUID postId) {
        return postRepository.findWithUser(postId).orElseThrow(PostNotFoundException::new);
    }

    private static Post mine(UUID userId, Post post) {
        if (!post.isWrittenBy(userId)) throw new NotAuthorException();
        return post;
    }

    private static void checkLevel(int level) {
        if (!JlptLevel.isValid(level)) throw new BoardLevelNotFoundException();
    }
}
