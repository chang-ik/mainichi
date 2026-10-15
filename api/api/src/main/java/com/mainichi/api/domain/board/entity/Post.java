package com.mainichi.api.domain.board.entity;

import com.mainichi.api.domain.user.entity.User;
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

/** 게시판 글. 학습 단계(level)별 게시판에 올라간다. */
@Entity
@Table(name = "post", indexes = @Index(name = "idx_post_level_created", columnList = "level, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    public static final int TITLE_MAX = 100;
    public static final int CONTENT_MAX = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 게시판 단계 (0=기초, 1=N1 … 5=N5). JlptLevel 참고 */
    @Column(nullable = false)
    private int level;

    @Column(length = TITLE_MAX, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Post(User user, int level, String title, String content) {
        this.user = user;
        this.level = level;
        this.title = title;
        this.content = content;
    }

    public boolean isWrittenBy(UUID userId) {
        return user.getId().equals(userId);
    }

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
