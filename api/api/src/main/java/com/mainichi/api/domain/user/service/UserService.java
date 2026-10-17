package com.mainichi.api.domain.user.service;

import com.mainichi.api.domain.auth.repository.RefreshTokenRepository;
import com.mainichi.api.domain.board.repository.CommentRepository;
import com.mainichi.api.domain.board.repository.PostRepository;
import com.mainichi.api.domain.quiz.repository.QuizRepository;
import com.mainichi.api.domain.study.repository.CardStateRepository;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.study.repository.UserFsrsParamRepository;
import com.mainichi.api.domain.user.entity.StudyArea;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.exception.UserNotFoundException;
import com.mainichi.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CardStateRepository cardStateRepository;
    private final UserFsrsParamRepository userFsrsParamRepository;
    private final QuizRepository quizRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

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

    public void updateProfile(UUID userId, String nickname, String timezone, Integer currentLevel,
                              Set<StudyArea> studyAreas){
        User user = getById(userId);
        user.updateProfile(nickname, timezone, currentLevel);
        if(studyAreas != null){
            user.changeStudyAreas(studyAreas);
        }
    }

    public void deleteAccount(UUID userId){
        //todo : 유저가 직접 만든 deck/card 기능이 생기면 그것도 삭제
        // 유저를 참조하는 테이블(FK)을 먼저 지워야 유저 삭제가 제약 위반으로 실패하지 않는다.
        refreshTokenRepository.deleteAllByUserId(userId);
        // 댓글(내 댓글 + 내 글에 달린 댓글) → 글 순서로 지워야 FK 제약에 걸리지 않는다
        commentRepository.deleteAllByUserIdOrPostUserId(userId);
        postRepository.deleteAllByUserId(userId);
        quizRepository.deleteAllByUserId(userId);
        reviewLogRepository.deleteAllByUserId(userId);
        cardStateRepository.deleteAllByUserId(userId);
        userFsrsParamRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }
}
