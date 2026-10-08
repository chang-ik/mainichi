package com.mainichi.api.domain.study.controller.rqrs;

import com.mainichi.api.domain.study.entity.UserFsrsParam;

public record StudySettingsResponse(
        int newCardsPerDay,
        int defaultNewCardsPerDay
) {
    public static StudySettingsResponse from(UserFsrsParam params) {
        return new StudySettingsResponse(params.getNewCardsPerDay(), UserFsrsParam.DEFAULT_NEW_CARDS_PER_DAY);
    }
}
