package com.mainichi.api.domain.board.controller.rqrs;

import com.mainichi.api.domain.board.entity.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank @Size(max = Comment.CONTENT_MAX) String content) {}
