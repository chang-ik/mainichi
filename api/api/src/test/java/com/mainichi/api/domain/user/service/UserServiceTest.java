package com.mainichi.api.domain.user.service;

import com.mainichi.api.domain.auth.repository.RefreshTokenRepository;
import com.mainichi.api.domain.study.repository.CardStateRepository;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.study.repository.UserFsrsParamRepository;
import com.mainichi.api.domain.user.entity.StudyArea;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import com.mainichi.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ReviewLogRepository reviewLogRepository;

    @Mock
    private CardStateRepository cardStateRepository;

    @Mock
    private UserFsrsParamRepository userFsrsParamRepository;

    @InjectMocks
    private UserService userService;

    private User newUser() {
        return User.builder().provider("kakao").socialId("12345").email("user@example.com").build();
    }

    @Test
    @DisplayName("이미 가입된 소셜 계정이면 기존 유저를 반환하고 신규가 아니다")
    void findExisting() {
        User existing = newUser();
        when(userRepository.findByProviderAndSocialId("kakao", "12345")).thenReturn(Optional.of(existing));

        UserService.FindOrCreateResult result = userService.findOrCreateBySocialId("kakao", "12345", "user@example.com");

        assertSame(existing, result.user());
        assertFalse(result.inNewUser());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입되지 않은 소셜 계정이면 새로 저장하고 신규로 표시한다")
    void createNew() {
        when(userRepository.findByProviderAndSocialId("kakao", "12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserService.FindOrCreateResult result = userService.findOrCreateBySocialId("kakao", "12345", "user@example.com");

        assertTrue(result.inNewUser());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("kakao", saved.getProvider());
        assertEquals("12345", saved.getSocialId());
        assertEquals("user@example.com", saved.getEmail());
    }

    @Test
    @DisplayName("이메일 동의가 없어 null이어도 가입할 수 있다")
    void createNewWithoutEmail() {
        when(userRepository.findByProviderAndSocialId("kakao", "999")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserService.FindOrCreateResult result = userService.findOrCreateBySocialId("kakao", "999", null);

        assertTrue(result.inNewUser());
        assertEquals(null, result.user().getEmail());
    }

    @Test
    @DisplayName("getById는 유저가 있으면 반환한다")
    void getById() {
        UUID id = UUID.randomUUID();
        User user = newUser();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertSame(user, userService.getById(id));
    }

    @Test
    @DisplayName("getById는 유저가 없으면 USER_NOT_FOUND 예외를 던진다")
    void getByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UserNotFoundException e = assertThrows(UserNotFoundException.class, () -> userService.getById(id));

        assertEquals("USER_NOT_FOUND", e.getMessage());
    }

    @Test
    @DisplayName("updateProfile은 조회한 유저의 프로필을 수정한다")
    void updateProfile() {
        UUID id = UUID.randomUUID();
        User user = newUser();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.updateProfile(id, "마이니치", "Asia/Tokyo", 3, Set.of(StudyArea.READING));

        assertEquals("마이니치", user.getNickname());
        assertEquals("Asia/Tokyo", user.getTimezone());
        assertEquals(3, user.getCurrentLevel());
        assertEquals(Set.of(StudyArea.WORD, StudyArea.READING), user.getStudyAreas());
    }

    @Test
    @DisplayName("updateProfile은 존재하지 않는 유저면 예외를 던진다")
    void updateProfileNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.updateProfile(id, "닉네임", null, null, null));
    }

    @Test
    @DisplayName("deleteAccount는 FK 제약을 피하려고 리프레시 토큰·학습 기록을 먼저 지운 뒤 유저를 삭제한다")
    void deleteAccount() {
        UUID id = UUID.randomUUID();

        userService.deleteAccount(id);

        InOrder inOrder = inOrder(refreshTokenRepository, reviewLogRepository, cardStateRepository,
                userFsrsParamRepository, userRepository);
        inOrder.verify(refreshTokenRepository).deleteAllByUserId(id);
        inOrder.verify(reviewLogRepository).deleteAllByUserId(id);
        inOrder.verify(cardStateRepository).deleteAllByUserId(id);
        inOrder.verify(userFsrsParamRepository).deleteByUserId(id);
        inOrder.verify(userRepository).deleteById(id);
    }
}
