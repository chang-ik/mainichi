package com.mainichi.api.domain.user.controller;

import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        user = User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        // JwtAuthenticationFilter가 하는 것처럼 principal에 userId를 넣는다.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /user/me: 내 프로필을 반환한다")
    void me() throws Exception {
        when(userService.getById(userId)).thenReturn(user);

        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.currentLevel").value(0))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
    }

    @Test
    @DisplayName("PATCH /user/me: 프로필을 수정하고 수정된 결과를 반환한다")
    void updateMe() throws Exception {
        when(userService.getById(userId)).thenReturn(user);
        // 서비스 대신 실제 엔티티 동작을 재현한다.
        org.mockito.Mockito.doAnswer(inv -> {
            user.updateProfile(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(userService).updateProfile(any(), any(), any());

        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "마이니치", "timezone": "Asia/Tokyo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("마이니치"))
                .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        verify(userService).updateProfile(userId, "마이니치", "Asia/Tokyo");
    }

    @Test
    @DisplayName("PATCH /user/me: 닉네임이 2자 미만이면 400")
    void updateMeNicknameTooShort() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "a"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /user/me: 닉네임이 20자를 넘으면 400")
    void updateMeNicknameTooLong() throws Exception {
        mockMvc.perform(patch("/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"" + "a".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("DELETE /user/me: 204를 반환하고 계정을 삭제한다")
    void deleteMe() throws Exception {
        mockMvc.perform(delete("/user/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(userId);
    }
}
