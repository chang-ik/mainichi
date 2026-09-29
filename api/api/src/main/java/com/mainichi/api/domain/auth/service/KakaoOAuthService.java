package com.mainichi.api.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthService {

    private final RestClient apiClient = RestClient.create("https://kapi.kakao.com");
    private final RestClient authClient = RestClient.create("https://kauth.kakao.com");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthService(
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret:}") String clientSecret,
            @Value("${kakao.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public record KakaoProfile(String socialId, String email){};

    /** 카카오가 리다이렉트로 내려준 인가 코드를 카카오 access token으로 교환한다. */
    public String exchangeCodeForAccessToken(String code){
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            KakaoTokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new InvalidSocialTokenException();
            }

            return response.accessToken();
        } catch(RestClientResponseException e) {
            throw new InvalidSocialTokenException();
        }
    }

    public KakaoProfile fetchProfile(String kakaoAccessToken){
        try {
            KakaoUserResponse response = apiClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null) {
                throw new InvalidSocialTokenException();
            }

            String email = response.kakaoAccount() != null ? response.kakaoAccount().email() : null;

            return new KakaoProfile(String.valueOf(response.id()), email);
        } catch(RestClientResponseException e) {
            throw new InvalidSocialTokenException();
        }
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ){}

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ){}

    private record KakaoAccount(String email){}
}
