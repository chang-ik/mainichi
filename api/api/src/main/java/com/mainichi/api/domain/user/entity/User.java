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
        if(nickname != null){
            this.nickname = nickname;
        }
        if(timezone != null){
            this.timezone = timezone;
        }
    }
}
