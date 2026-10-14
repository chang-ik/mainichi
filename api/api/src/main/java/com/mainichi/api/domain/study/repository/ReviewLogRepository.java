package com.mainichi.api.domain.study.repository;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.stats.service.ReviewRow;
import com.mainichi.api.domain.study.entity.ReviewLog;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, UUID> {

    /** from 이후 tag 카드에서 복습 "전" 단계가 state 였던 기록 수. NEW 면 오늘 새로 본 카드 수가 된다. */
    @Query("""
            select count(r) from ReviewLog r
            where r.user.id = :userId and r.card.cardTag = :tag and r.state = :state and r.reviewTime >= :from
            """)
    long countSince(@Param("userId") UUID userId, @Param("tag") String tag, @Param("state") CardPhase state,
                    @Param("from") LocalDateTime from);

    /**
     * from 이후 처음 학습한(복습 전 단계가 NEW 였던) 카드. 최근에 배운 카드부터.
     * 카드는 NEW 상태로 한 번만 평가되므로 같은 카드가 두 번 나오지 않는다.
     */
    @Query("""
            select r.card from ReviewLog r
            where r.user.id = :userId and r.state = :state and r.reviewTime >= :from
            order by r.reviewTime desc
            """)
    List<Card> findCardsSince(@Param("userId") UUID userId, @Param("state") CardPhase state,
                              @Param("from") LocalDateTime from, Pageable pageable);

    @Query("""
            select count(r) from ReviewLog r
            where r.user.id = :userId and r.state = :state and r.reviewTime >= :from
            """)
    long countAllSince(@Param("userId") UUID userId, @Param("state") CardPhase state,
                       @Param("from") LocalDateTime from);

    /** 통계용: from 이후 tags 카드의 복습 기록 (시간 순) */
    @Query("""
            select new com.mainichi.api.domain.stats.service.ReviewRow(
                r.reviewTime, r.rating, r.state, r.elapsedDays, r.durationMs)
            from ReviewLog r
            where r.user.id = :userId and r.card.cardTag in :tags and r.reviewTime >= :from
            order by r.reviewTime
            """)
    List<ReviewRow> findRowsSince(@Param("userId") UUID userId, @Param("tags") Collection<String> tags,
                                  @Param("from") LocalDateTime from);

    @Modifying
    @Query("delete from ReviewLog r where r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
