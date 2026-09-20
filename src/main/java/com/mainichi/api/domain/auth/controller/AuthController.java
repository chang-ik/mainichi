package com.mainichi.api.domain.auth.controller;

import com.mainichi.api.domain.auth.controller.rqrs.LoginRequest;
import com.mainichi.api.domain.auth.controller.rqrs.LoginResponse;
import com.mainichi.api.domain.auth.controller.rqrs.RefreshRequest;
import com.mainichi.api.domain.auth.controller.rqrs.RefreshResponse;
import com.mainichi.api.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest
    ){
        AuthService.LoginResult result = authService.login(request.provider(), request.socialToken(), deviceInfo(httpRequest));
        return ResponseEntity.ok(toLoginResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest reqeust, HttpServletRequest httpRequest
    ){
        AuthService.LoginResult result = authService.refresh(reqeust.refreshToken(), deviceInfo(httpRequest));
        return ResponseEntity.ok(new RefreshResponse(
                result.accessToken(), result.refreshToken(), "Bearer", result.expiresIn()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request){
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse toLoginResponse(AuthService.LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.expiresIn(),
                result.isNewUser(),
                new LoginResponse.UserSummary(
                        result.user().getId().toString(),
                        result.user().getNickname(),
                        result.user().getEmail(),
                        result.user().getProvider(),
                        result.user().getCurrentLevel(),
                        result.user().getTimezone()
                )
        );
    }

    private String deviceInfo(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
