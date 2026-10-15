package com.mainichi.api.domain.board.controller.rqrs;

import com.mainichi.api.domain.board.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 글쓰기·수정 */
public record PostRequest(
        @NotBlank @Size(max = Post.TITLE_MAX) String title,
        @NotBlank @Size(max = Post.CONTENT_MAX) String content
) {}
