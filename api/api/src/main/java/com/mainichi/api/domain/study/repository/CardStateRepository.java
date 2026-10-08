package com.mainichi.api.domain.study.repository;

import com.mainichi.api.domain.study.entity.CardState;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardStateRepository extends JpaRepository<CardState, UUID> {

    @Query("select s from CardState s where s.user.id = :userId and s.card.id = :cardId")
    Optional<CardState> findByUserIdAndCardId(@Param("userId") UUID userId, @Param("cardId") UUID cardId);

    @Query("""
            select count(s) from CardState s
            where s.user.id = :userId and s.card.cardTag = :tag and s.card.level = :level
              and s.state in :states and s.due <= :until
            """)
    long countDue(@Param("userId") UUID userId, @Param("tag") String tag, @Param("level") int level,
                  @Param("states") Collection<CardPhase> states, @Param("until") LocalDateTime until);

    /** 기한이 지난 순서대로. 카드를 함께 가져온다. */
    @Query("""
            select s from CardState s join fetch s.card c
            where s.user.id = :userId and c.cardTag = :tag and c.level = :level
              and s.state in :states and s.due <= :until
            order by s.due
            """)
    List<CardState> findDue(@Param("userId") UUID userId, @Param("tag") String tag, @Param("level") int level,
                            @Param("states") Collection<CardPhase> states, @Param("until") LocalDateTime until,
                            Pageable pageable);

    @Query("""
            select min(s.due) from CardState s
            where s.user.id = :userId and s.card.cardTag = :tag and s.card.level = :level and s.state in :states
            """)
    Optional<LocalDateTime> findEarliestDue(@Param("userId") UUID userId, @Param("tag") String tag,
                                            @Param("level") int level, @Param("states") Collection<CardPhase> states);

    @Modifying
    @Query("delete from CardState s where s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
