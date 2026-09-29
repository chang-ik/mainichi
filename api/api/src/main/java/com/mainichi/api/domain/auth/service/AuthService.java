package com.mainichi.api.domain.auth.service;

import com.mainichi.api.domain.auth.entity.RefreshToken;
import com.mainichi.api.domain.auth.exception.InvalidRefreshTokenException;
import com.mainichi.api.domain.auth.exception.UnsupportedProviderException;
import com.mainichi.api.domain.auth.repository.RefreshTokenRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import com.mainichi.api.global.common.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final long REFRESH_TOKEN_VALIDITY_DAYS = 30;

    private final UserService userService;
    private final KakaoOAuthService kakaoOAuthService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public record LoginResult(
            String accessToken,
            String refreshToken,
            long expiresIn,
            boolean isNewUser,
            User user
    ){}

    @Transactional
    public LoginResult login(String provider, String authorizationCode, String deviceInfo){
        //현재는 kakao 로그인만 지원하므로 kakao가 아니면 에러. 추후 구글 추가되면 수정 필
        if (!"kakao".equals(provider)) {
            throw new UnsupportedProviderException();
        }

        String socialAccessToken = kakaoOAuthService.exchangeCodeForAccessToken(authorizationCode);
        KakaoOAuthService.KakaoProfile profile = kakaoOAuthService.fetchProfile(socialAccessToken);

        UserService.FindOrCreateResult result = userService.findOrCreateBySocialId(
                provider, profile.socialId(), profile.email()
        );

        String accessToken = jwtTokenProvider.createAccessToken(result.user().getId());
        String rawRefreshToken = generateRawToken();
        storeRefreshToken(result.user(), rawRefreshToken, deviceInfo);

        return(new LoginResult(
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenValiditySeconds(),
                result.inNewUser(),
                result.user()
        ));
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken, String deviceInfo){
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if(!existing.isValid()){
            throw new InvalidRefreshTokenException();
        }

        existing.revoke();
        existing.markUsed();

        String newAccessToken = jwtTokenProvider.createAccessToken(existing.getUser().getId());
        String newRefreshToken = generateRawToken();
        storeRefreshToken(existing.getUser(), newRefreshToken, deviceInfo);

        return new LoginResult(
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getAccessTokenValiditySeconds(),
                false,
                existing.getUser()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken){
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    private String generateRawToken(){
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void storeRefreshToken(User user, String rawToken, String deviceInfo){
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(hash(rawToken))
                        .deviceInfo(deviceInfo)
                        .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS))
                        .build()
        );
    }

    private String hash(String value) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes()));
        } catch(NoSuchAlgorithmException e){
            throw new IllegalStateException(e);
        }
    }
}
