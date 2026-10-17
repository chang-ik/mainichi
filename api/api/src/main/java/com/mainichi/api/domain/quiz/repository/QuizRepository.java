package com.mainichi.api.domain.quiz.repository;

import com.mainichi.api.domain.quiz.QuizKind;
import com.mainichi.api.domain.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findFirstByUserIdAndKindAndCreatedTimeGreaterThanEqualOrderByCreatedTimeDesc(
            UUID userId, QuizKind kind, LocalDateTime from);

    long countByUserIdAndKindAndCreatedTimeGreaterThanEqual(UUID userId, QuizKind kind, LocalDateTime from);

    /** from 이후 채점까지 끝낸 문제 수 */
    long countByUserIdAndKindAndSubmittedTimeGreaterThanEqual(UUID userId, QuizKind kind, LocalDateTime from);

    Optional<Quiz> findByIdAndUserIdAndKind(UUID id, UUID userId, QuizKind kind);

    @Modifying
    @Query("delete from Quiz q where q.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
