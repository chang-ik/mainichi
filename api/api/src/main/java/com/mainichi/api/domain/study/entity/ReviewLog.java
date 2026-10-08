package com.mainichi.api.domain.study.entity;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.study.fsrs.CardPhase;
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

/**
 * 복습 기록. 나중에 유저별 FSRS 파라미터 최적화와 통계 화면에 쓴다.
 * state 는 복습 "전" 단계(NEW 면 그날 처음 본 새 카드), stability/difficulty/scheduled_days 는 복습 "후" 값이다.
 */
@Entity
@Table(name = "review_log", indexes = @Index(name = "idx_review_log_user_time", columnList = "user_id, review_time"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CardPhase state;

    @Column(nullable = false)
    private double stability;

    @Column(nullable = false)
    private double difficulty;

    @Column(name = "elapsed_days", nullable = false)
    private int elapsedDays;

    @Column(name = "scheduled_days", nullable = false)
    private int scheduledDays;

    @Column(name = "review_time", nullable = false)
    private LocalDateTime reviewTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public ReviewLog(User user, Card card, int rating, CardPhase state, double stability, double difficulty,
                     int elapsedDays, int scheduledDays, LocalDateTime reviewTime) {
        this.user = user;
        this.card = card;
        this.rating = rating;
        this.state = state;
        this.stability = stability;
        this.difficulty = difficulty;
        this.elapsedDays = elapsedDays;
        this.scheduledDays = scheduledDays;
        this.reviewTime = reviewTime;
    }
}
