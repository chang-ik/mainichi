package com.mainichi.api.global.config;

import com.mainichi.api.domain.auth.controller.RefreshTokenCookieProvider;
import com.mainichi.api.domain.user.controller.UserController;
import com.mainichi.api.domain.user.service.UserService;
import com.mainichi.api.global.common.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class SecurityConfigErrorDispatchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    @DisplayName("/error 디스패치는 인증 없이 통과한다 (막히면 400/500 이 전부 403 으로 바뀐다)")
    void errorDispatchIsPermitted() throws Exception {
        mockMvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    @DisplayName("/error 를 열어도 다른 API 는 여전히 인증이 필요하다")
    void otherEndpointsStillRequireAuth() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isForbidden());
    }
}
