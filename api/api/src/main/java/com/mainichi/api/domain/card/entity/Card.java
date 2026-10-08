package com.mainichi.api.domain.card.entity;

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
@Table(name = "card", indexes = @Index(name = "idx_card_tag_level", columnList = "card_tag, level"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    public static final String TAG_WORD = "word";
    public static final String TAG_GRAMMAR = "grammar";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    /** 카드 종류. word(단어), grammar(문법). 나중에 sentence(문장) 등이 추가될 수 있다. */
    @Column(name = "card_tag", length = 20, nullable = false)
    private String cardTag;

    /** 단계 (0=기초, 1=N1 … 5=N5). JlptLevel 참고. */
    @Column(nullable = false)
    private Integer level;

    /** 앞면. 후리가나는 한자 뒤 대괄호로 인라인 표기한다. 예) 食[た]べる */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String front;

    /** 뒷면. 단어·문법 카드 모두 WordBack(JSON) 형식. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String back;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Card(Deck deck, String cardTag, Integer level, String front, String back) {
        this.deck = deck;
        this.cardTag = cardTag;
        this.level = level;
        this.front = front;
        this.back = back;
    }
}
