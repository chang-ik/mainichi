package com.mainichi.api.domain.board.repository;

import com.mainichi.api.domain.board.controller.rqrs.PostSummary;
import com.mainichi.api.domain.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /** 단계별 목록 (최신순). 작성자 닉네임과 댓글 수를 함께 */
    @Query(value = """
            select new com.mainichi.api.domain.board.controller.rqrs.PostSummary(
                p.id, p.title, u.nickname, p.createdAt,
                (select count(c) from Comment c where c.post = p))
            from Post p join p.user u
            where p.level = :level
            order by p.createdAt desc, p.id desc
            """,
            countQuery = "select count(p) from Post p where p.level = :level")
    Page<PostSummary> findSummaries(@Param("level") int level, Pageable pageable);

    @Query("select p from Post p join fetch p.user where p.id = :id")
    Optional<Post> findWithUser(@Param("id") UUID id);

    @Modifying
    @Query("delete from Post p where p.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
