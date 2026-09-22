package com.mainichi.api.global.config;

import com.mainichi.api.domain.user.controller.UserController;
import com.mainichi.api.domain.user.service.UserService;
import com.mainichi.api.global.common.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class SecurityConfigCorsTest {

    private static final String FRONT_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("프리플라이트: 허용된 origin 은 인증 없이도 통과하고 CORS 헤더를 받는다")
    void preflight_allowedOrigin() throws Exception {
        mockMvc.perform(options("/user/me")
                        .header("Origin", FRONT_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT_ORIGIN))
                .andExpect(result -> {
                    String allowed = result.getResponse().getHeader("Access-Control-Allow-Headers");
                    assertThat(allowed).isNotNull();
                    assertThat(allowed.toLowerCase()).contains("authorization").contains("content-type");
                });
    }

    @Test
    @DisplayName("프리플라이트: 허용되지 않은 origin 은 거절된다")
    void preflight_disallowedOrigin() throws Exception {
        mockMvc.perform(options("/user/me")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
