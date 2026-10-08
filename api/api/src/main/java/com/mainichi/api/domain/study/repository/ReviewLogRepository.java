package com.mainichi.api.domain.study.repository;

import com.mainichi.api.domain.study.entity.ReviewLog;
import com.mainichi.api.domain.study.fsrs.CardPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, UUID> {

    /** from 이후 tag 카드에서 복습 "전" 단계가 state 였던 기록 수. NEW 면 오늘 새로 본 카드 수가 된다. */
    @Query("""
            select count(r) from ReviewLog r
            where r.user.id = :userId and r.card.cardTag = :tag and r.state = :state and r.reviewTime >= :from
            """)
    long countSince(@Param("userId") UUID userId, @Param("tag") String tag, @Param("state") CardPhase state,
                    @Param("from") LocalDateTime from);

    @Modifying
    @Query("delete from ReviewLog r where r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
