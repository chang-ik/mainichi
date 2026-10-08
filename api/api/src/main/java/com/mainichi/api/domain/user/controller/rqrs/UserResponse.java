package com.mainichi.api.domain.user.controller.rqrs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mainichi.api.domain.user.entity.StudyArea;
import com.mainichi.api.domain.user.entity.User;

import java.util.List;

public record UserResponse(
        String id,
        String nickname,
        String email,
        String provider,
        Integer currentLevel,
        String timezone,
        List<StudyArea> studyAreas
) {
    public static UserResponse from(User user){
        return new UserResponse(
                user.getId().toString(),
                user.getNickname(),
                user.getEmail(),
                user.getProvider(),
                user.getCurrentLevel(),
                user.getTimezone(),
                List.copyOf(user.getStudyAreas())
        );
    }
}