package com.mainichi.api.domain.card.repository;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.entity.Deck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    @Query("select c.front from Card c where c.deck = :deck")
    List<String> findFrontsByDeck(@Param("deck") Deck deck);

    /** 이 유저가 한 번도 복습하지 않은 카드 수 */
    @Query("""
            select count(c) from Card c
            where c.cardTag = :tag and c.level = :level
              and not exists (select 1 from CardState s where s.card = c and s.user.id = :userId)
            """)
    long countUnseen(@Param("userId") UUID userId, @Param("tag") String tag, @Param("level") int level);

    @Query("""
            select c from Card c
            where c.cardTag = :tag and c.level = :level
              and not exists (select 1 from CardState s where s.card = c and s.user.id = :userId)
            order by c.createdAt, c.id
            """)
    List<Card> findUnseen(@Param("userId") UUID userId, @Param("tag") String tag, @Param("level") int level,
                          Pageable pageable);
}
