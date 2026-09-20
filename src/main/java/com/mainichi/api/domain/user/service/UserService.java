package com.mainichi.api.domain.user.service;

import com.mainichi.api.domain.user.entity.User;
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
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
    }

    public void updateProfile(UUID userId, String nickname, String timezone){
        User user = getById(userId);
        user.updateProfile(nickname, timezone);
    }

    public void deleteAccount(UUID userId){
        //todo : 나중에 deck/card_state/review_log/user_fsrs_param/refresh_token 까지 삭제
        userRepository.deleteById(userId);
    }
}
