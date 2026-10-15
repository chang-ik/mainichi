package com.mainichi.api.domain.board.controller.rqrs;

import java.time.LocalDateTime;
import java.util.UUID;

/** 게시판 목록의 글 한 줄 */
public record PostSummary(UUID id, String title, String author, LocalDateTime createdAt, long commentCount) {}
