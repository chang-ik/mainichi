package com.mainichi.api.domain.user.controller;

import com.mainichi.api.domain.auth.controller.RefreshTokenCookieProvider;
import com.mainichi.api.domain.user.controller.rqrs.UpdateProfileRequest;
import com.mainichi.api.domain.user.controller.rqrs.UserResponse;
import com.mainichi.api.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/user/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @GetMapping
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UUID userId){
        return ResponseEntity.ok(UserResponse.from(userService.getById(userId)));
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateme(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateProfileRequest request
    ){
        userService.updateProfile(userId, request.nickname(), request.timezone());
        return ResponseEntity.ok(UserResponse.from(userService.getById(userId)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteme(@AuthenticationPrincipal UUID userId){
        userService.deleteAccount(userId);
        // DB 의 리프레시 토큰은 이미 지워졌으니 브라우저에 남은 쿠키도 함께 지운다.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.expire().toString())
                .build();
    }
}
