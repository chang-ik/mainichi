package com.mainichi.api.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mainichi.api.domain.auth.exception.InvalidSocialTokenException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthService {

    private final RestClient restClient = RestClient.create("https://kapi.kakao.com");

    public record KakaoProfile(String socialId, String email){};

    public KakaoProfile fetchProfile(String kakaoAccessToken){
        try {
            KakaoUserResponse response = restClient.get()
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

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ){}

    private record KakaoAccount(String email){}
}
