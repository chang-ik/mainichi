package com.mainichi.api.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uq_users_provider_social_id", columnNames = {"provider", "social_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(length = 20)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(length = 50, nullable = false)
    private String provider;

    @Column(name = "social_id", length = 50, nullable = false)
    private String socialId;

    @Column(name = "current_level", nullable = false)
    private Integer currentLevel = 0;

    @Column(length = 50, nullable = false)
    private String timezone = "Asia/Seoul";

    /** 단어 외에 추가로 고른 학습 영역. 단어는 항상 포함되므로 저장하지 않는다. */
    @Convert(converter = StudyAreaSetConverter.class)
    @Column(name = "study_areas", length = 100)
    private Set<StudyArea> extraStudyAreas = EnumSet.noneOf(StudyArea.class);

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String provider, String socialId){
        this.email = email;
        this.provider = provider;
        this.socialId = socialId;
    }

    public void updateProfile(String nickname, String timezone){
        updateProfile(nickname, timezone, null);
    }

    public void updateProfile(String nickname, String timezone, Integer currentLevel){
        if(nickname != null){
            this.nickname = nickname;
        }
        if(timezone != null){
            this.timezone = timezone;
        }
        if(currentLevel != null){
            this.currentLevel = currentLevel;
        }
    }

    /** 학습 영역을 바꾼다. 단어(WORD)는 항상 포함이라 넘겨도 무시한다. */
    public void changeStudyAreas(Set<StudyArea> areas){
        EnumSet<StudyArea> extra = EnumSet.noneOf(StudyArea.class);
        extra.addAll(areas);
        extra.remove(StudyArea.WORD);
        this.extraStudyAreas = extra;
    }

    /** 단어를 포함한 학습 영역 전체 (enum 순서) */
    public Set<StudyArea> getStudyAreas(){
        EnumSet<StudyArea> all = EnumSet.of(StudyArea.WORD);
        if(extraStudyAreas != null){
            all.addAll(extraStudyAreas);
        }
        return Collections.unmodifiableSet(all);
    }
}
