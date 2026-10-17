package com.mainichi.api.domain.quiz.controller;

import com.mainichi.api.domain.quiz.QuizKind;
import com.mainichi.api.domain.quiz.controller.rqrs.QuizResponse;
import com.mainichi.api.domain.quiz.controller.rqrs.SubmitQuizRequest;
import com.mainichi.api.domain.quiz.controller.rqrs.TodayQuizResponse;
import com.mainichi.api.domain.quiz.exception.QuizKindNotFoundException;
import com.mainichi.api.domain.quiz.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** kind = reading(독해) / listening(청해) */
@RestController
@RequestMapping("/quiz/{kind}")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /** 오늘 새로 배운 카드 수와 오늘 만든 최근 문제 */
    @GetMapping("/today")
    public ResponseEntity<TodayQuizResponse> today(@AuthenticationPrincipal UUID userId, @PathVariable String kind) {
        return ResponseEntity.ok(quizService.getToday(userId, kindOf(kind)));
    }

    /** 새 문제를 만든다 (Claude 응답까지 수십 초 걸릴 수 있다) */
    @PostMapping
    public ResponseEntity<QuizResponse> create(@AuthenticationPrincipal UUID userId, @PathVariable String kind) {
        return ResponseEntity.ok(quizService.create(userId, kindOf(kind)));
    }

    /** 답을 내고 정답·해설·해석을 받는다 */
    @PostMapping("/{quizId}/answers")
    public ResponseEntity<QuizResponse> submit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String kind,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request
    ) {
        return ResponseEntity.ok(quizService.submit(userId, kindOf(kind), quizId, request.answers()));
    }

    private static QuizKind kindOf(String path) {
        return QuizKind.fromPath(path).orElseThrow(QuizKindNotFoundException::new);
    }
}
