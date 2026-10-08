package com.mainichi.api.domain.study.controller;

import com.mainichi.api.domain.study.controller.rqrs.StudySettingsRequest;
import com.mainichi.api.domain.study.controller.rqrs.StudySettingsResponse;
import com.mainichi.api.domain.study.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/study/settings")
@RequiredArgsConstructor
public class StudySettingsController {

    private final StudyService studyService;

    /** 하루 학습량 */
    @GetMapping
    public ResponseEntity<StudySettingsResponse> get(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(studyService.getSettings(userId));
    }

    @PatchMapping
    public ResponseEntity<StudySettingsResponse> update(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody StudySettingsRequest request
    ) {
        return ResponseEntity.ok(studyService.updateSettings(userId, request.newCardsPerDay()));
    }
}
