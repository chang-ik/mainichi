package com.mainichi.api.domain.study.entity;

import com.mainichi.api.domain.study.fsrs.FsrsSettings;
import com.mainichi.api.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** 유저별 FSRS 설정. 처음 학습할 때 기본값으로 만들어진다. */
@Entity
@Table(name = "user_fsrs_param")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFsrsParam {

    public static final int DEFAULT_NEW_CARDS_PER_DAY = 20;
    public static final int DEFAULT_MAX_REVIEWS_PER_DAY = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Convert(converter = JsonNumberListConverter.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private List<Double> weights;

    @Column(name = "request_retention", nullable = false)
    private double requestRetention;

    @Column(name = "maximum_interval", nullable = false)
    private int maximumInterval;

    @Column(name = "new_cards_per_day", nullable = false)
    private int newCardsPerDay;

    @Column(name = "max_reviews_per_day", nullable = false)
    private int maxReviewsPerDay;

    /** 학습 단계(분). 예) [1, 10] */
    @Convert(converter = JsonNumberListConverter.class)
    @Column(name = "learning_steps", columnDefinition = "JSON", nullable = false)
    private List<Double> learningSteps;

    /** 재학습 단계(분). 예) [10] */
    @Convert(converter = JsonNumberListConverter.class)
    @Column(name = "relearning_steps", columnDefinition = "JSON", nullable = false)
    private List<Double> relearningSteps;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static UserFsrsParam defaultsFor(User user) {
        FsrsSettings d = FsrsSettings.defaults();
        UserFsrsParam p = new UserFsrsParam();
        p.user = user;
        p.weights = Arrays.stream(d.weights()).boxed().toList();
        p.requestRetention = d.requestRetention();
        p.maximumInterval = d.maximumInterval();
        p.newCardsPerDay = DEFAULT_NEW_CARDS_PER_DAY;
        p.maxReviewsPerDay = DEFAULT_MAX_REVIEWS_PER_DAY;
        p.learningSteps = toMinutes(d.learningSteps());
        p.relearningSteps = toMinutes(d.relearningSteps());
        return p;
    }

    /** 하루 학습량(하루에 새로 꺼낼 카드 수)을 바꾼다. */
    public void changeNewCardsPerDay(int newCardsPerDay) {
        this.newCardsPerDay = newCardsPerDay;
    }

    public FsrsSettings toSettings() {
        return new FsrsSettings(
                weights.stream().mapToDouble(Double::doubleValue).toArray(),
                requestRetention,
                maximumInterval,
                fromMinutes(learningSteps),
                fromMinutes(relearningSteps)
        );
    }

    private static List<Double> toMinutes(List<Duration> steps) {
        return steps.stream().map(s -> s.toSeconds() / 60.0).toList();
    }

    private static List<Duration> fromMinutes(List<Double> minutes) {
        return minutes.stream().map(m -> Duration.ofSeconds(Math.round(m * 60))).toList();
    }
}
