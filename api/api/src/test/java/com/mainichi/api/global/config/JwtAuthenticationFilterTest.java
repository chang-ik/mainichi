package com.mainichi.api.global.config;

import com.mainichi.api.global.common.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";

    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        provider = new JwtTokenProvider(SECRET, 3600);
        filter = new JwtAuthenticationFilter(provider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 userId를 principal로 인증을 설정한다")
    void validToken() throws Exception {
        UUID userId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken(userId));

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(userId, authentication.getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 다음 필터로 넘긴다")
    void noHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 인증하지 않는다")
    void nonBearerScheme() throws Exception {
        request.addHeader("Authorization", "Basic abc123");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("잘못된 토큰이면 인증하지 않고 다음 필터로 넘긴다")
    void invalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer not-a-valid-token");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("만료된 토큰이면 인증하지 않는다")
    void expiredToken() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -10);
        request.addHeader("Authorization", "Bearer " + expiredProvider.createAccessToken(UUID.randomUUID()));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("잘못된 토큰이면 이전 요청에서 남은 인증 정보를 지운다")
    void invalidTokenClearsStaleContext() throws Exception {
        filter.doFilter(requestWith(provider.createAccessToken(UUID.randomUUID())), response, chain);

        filter.doFilter(requestWith("garbage"), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }
}
