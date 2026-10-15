package com.mainichi.api.domain.board.repository;

import com.mainichi.api.domain.board.entity.Comment;
import com.mainichi.api.domain.board.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** 글의 댓글 (오래된 순). 작성자를 함께 가져온다 */
    @Query("select c from Comment c join fetch c.user where c.post = :post order by c.createdAt, c.id")
    List<Comment> findByPostWithUser(@Param("post") Post post);

    @Modifying
    @Query("delete from Comment c where c.post = :post")
    void deleteAllByPost(@Param("post") Post post);

    /** 탈퇴: 이 유저가 쓴 댓글과, 이 유저의 글에 달린 남의 댓글까지 */
    @Modifying
    @Query("""
            delete from Comment c
            where c.user.id = :userId or c.post.id in (select p.id from Post p where p.user.id = :userId)
            """)
    void deleteAllByUserIdOrPostUserId(@Param("userId") UUID userId);
}
