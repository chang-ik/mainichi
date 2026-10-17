package com.mainichi.api.domain.quiz.entity;

import com.mainichi.api.domain.quiz.QuizKind;
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
 * Claude 가 만든 JLPT 형식 문제 한 세트 (독해 또는 청해). 지문/대화 하나에 문제 2개.
 * script / questions / sourceCards / selectedAnswers 는 QuizCodec 으로 JSON 변환해 저장한다.
 */
@Entity
@Table(name = "quiz", indexes = @Index(name = "idx_quiz_user_kind_time", columnList = "user_id, kind, created_time"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private QuizKind kind;

    /** 만들 때의 학습 단계 (0=기초, 1=N1 … 5=N5) */
    @Column(nullable = false)
    private int level;

    /** 제목 (후리가나 대괄호 표기) */
    @Column(length = 200, nullable = false)
    private String title;

    /** 독해: 지문. 청해: 대화 전에 읽어 주는 상황 설명. 후리가나 대괄호 표기 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String passage;

    /** 청해 대화. List<ListeningLine> JSON. 독해는 null */
    @Column(columnDefinition = "TEXT")
    private String script;

    /** 지문/대화 한국어 해석 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String translation;

    /** List<QuizQuestion> JSON */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String questions;

    /** 문제에 쓰라고 넘긴 오늘의 카드. List<QuizSourceCard> JSON */
    @Column(name = "source_cards", columnDefinition = "TEXT", nullable = false)
    private String sourceCards;

    /** 고른 보기 번호(0~3) JSON 배열. 아직 안 풀었으면 null */
    @Column(name = "selected_answers", length = 50)
    private String selectedAnswers;

    /** 문제를 만든 시각 (서버 시계 기준, 하루 개수 세는 데 쓴다) */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "submitted_time")
    private LocalDateTime submittedTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Quiz(User user, QuizKind kind, int level, String title, String passage, String script,
                String translation, String questions, String sourceCards, LocalDateTime createdTime) {
        this.user = user;
        this.kind = kind;
        this.level = level;
        this.title = title;
        this.passage = passage;
        this.script = script;
        this.translation = translation;
        this.questions = questions;
        this.sourceCards = sourceCards;
        this.createdTime = createdTime;
    }

    public boolean isSubmitted() {
        return submittedTime != null;
    }

    public void submit(String selectedAnswers, LocalDateTime now) {
        this.selectedAnswers = selectedAnswers;
        this.submittedTime = now;
    }
}
