package com.mainichi.api.domain.study.exception;

/** /study/{kind} 에 없는 학습 종류가 들어온 경우 */
public class StudyKindNotFoundException extends RuntimeException {
    public StudyKindNotFoundException() {
        super("STUDY_KIND_NOT_FOUND");
    }
}
