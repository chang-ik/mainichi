package com.mainichi.api.domain.study.controller;

import com.mainichi.api.domain.study.StudyKind;
import com.mainichi.api.domain.study.controller.rqrs.ReviewRequest;
import com.mainichi.api.domain.study.controller.rqrs.StudyQueueResponse;
import com.mainichi.api.domain.study.exception.StudyKindNotFoundException;
import com.mainichi.api.domain.study.fsrs.Rating;
import com.mainichi.api.domain.study.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    /** 지금 공부할 카드와 오늘 남은 개수. kind = words(단어) / grammar(문법) */
    @GetMapping("/{kind}")
    public ResponseEntity<StudyQueueResponse> queue(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String kind
    ) {
        return ResponseEntity.ok(studyService.getQueue(userId, kindOf(kind)));
    }

    /** 카드를 평가하고 다음 카드를 받는다 */
    @PostMapping("/{kind}/{cardId}/review")
    public ResponseEntity<StudyQueueResponse> review(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String kind,
            @PathVariable UUID cardId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.ok(studyService.review(userId, kindOf(kind), cardId, Rating.fromValue(request.rating())));
    }

    private static StudyKind kindOf(String path) {
        return StudyKind.fromPath(path).orElseThrow(StudyKindNotFoundException::new);
    }
}
