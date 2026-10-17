package com.mainichi.api.domain.stats.controller;

import com.mainichi.api.domain.stats.controller.rqrs.StatsResponse;
import com.mainichi.api.domain.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 학습 통계. deck = all / words / grammar, days = 30 / 90 / 365 */
    @GetMapping
    public ResponseEntity<StatsResponse> get(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "all") String deck,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(statsService.get(userId, deck, days));
    }
}
