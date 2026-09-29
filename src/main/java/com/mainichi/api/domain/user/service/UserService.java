package com.mainichi.api.domain.user.service;

import com.mainichi.api.domain.auth.repository.RefreshTokenRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import com.mainichi.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public record FindOrCreateResult(User user, boolean inNewUser){}

    public FindOrCreateResult findOrCreateBySocialId(
            String provider, String socialId, String email
    ){
        return userRepository.findByProviderAndSocialId(provider, socialId)
                .map(existing -> new FindOrCreateResult(existing, false))
                .orElseGet(() ->{
                    User created = userRepository.save(
                            User.builder()
                                    .provider(provider)
                                    .socialId(socialId)
                                    .email(email)
                                    .build()
                    );
                    return new FindOrCreateResult(created, true);
                });
    }

    @Transactional(readOnly = true)
    public User getById(UUID userId){
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    public void updateProfile(UUID userId, String nickname, String timezone){
        User user = getById(userId);
        user.updateProfile(nickname, timezone);
    }

    public void deleteAccount(UUID userId){
        //todo : 나중에 deck/card_state/review_log/user_fsrs_param 까지 삭제
        // 유저를 참조하는 테이블(FK)을 먼저 지워야 유저 삭제가 제약 위반으로 실패하지 않는다.
        refreshTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
    }
}
