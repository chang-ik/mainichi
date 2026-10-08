package com.mainichi.api.domain.study.entity;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import com.mainichi.api.domain.study.fsrs.FsrsCard;
import com.mainichi.api.domain.study.fsrs.ReviewOutcome;
import com.mainichi.api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** 유저별 카드 학습 상태. 한 번이라도 복습한 카드만 행이 있다 (없으면 새 카드). */
@Entity
@Table(
        name = "card_state",
        uniqueConstraints = @UniqueConstraint(name = "uq_card_state_user_card", columnNames = {"user_id", "card_id"}),
        indexes = @Index(name = "idx_card_state_user_due", columnList = "user_id, state, due")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardState {

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CardPhase state;

    /** 학습/재학습 단계 인덱스. REVIEW 면 NULL. */
    private Integer step;

    @Column(nullable = false)
    private LocalDateTime due;

    @Column(nullable = false)
    private double stability;

    @Column(nullable = false)
    private double difficulty;

    @Column(name = "elapsed_days", nullable = false)
    private int elapsedDays;

    @Column(name = "scheduled_days", nullable = false)
    private int scheduledDays;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "last_review", nullable = false)
    private LocalDateTime lastReview;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static CardState first(User user, Card card, ReviewOutcome outcome) {
        CardState state = new CardState();
        state.user = user;
        state.card = card;
        state.apply(outcome, CardPhase.NEW);
        return state;
    }

    public FsrsCard toFsrsCard() {
        return new FsrsCard(state, step, stability, difficulty, due, lastReview);
    }

    /** 복습 결과를 반영한다. REVIEW 에서 틀리면(RELEARNING 으로 가면) lapses 가 오른다. */
    public void apply(ReviewOutcome outcome, CardPhase before) {
        FsrsCard next = outcome.card();
        if (before == CardPhase.REVIEW && next.phase() == CardPhase.RELEARNING) {
            this.lapses++;
        }
        this.state = next.phase();
        this.step = next.step();
        this.stability = next.stability();
        this.difficulty = next.difficulty();
        this.due = next.due();
        this.lastReview = next.lastReview();
        this.elapsedDays = outcome.elapsedDays();
        this.scheduledDays = outcome.scheduledDays();
        this.reps++;
    }
}
