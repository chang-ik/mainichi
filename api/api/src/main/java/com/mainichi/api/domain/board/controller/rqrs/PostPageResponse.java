package com.mainichi.api.domain.board.controller.rqrs;

import java.util.List;

/**
 * 단계별 게시판 한 페이지.
 *
 * @param page 0부터 시작
 */
public record PostPageResponse(int level, List<PostSummary> posts, int page, int totalPages, long totalPosts) {}
