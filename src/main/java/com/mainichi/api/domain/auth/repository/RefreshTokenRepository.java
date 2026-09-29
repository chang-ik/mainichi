package com.mainichi.api.domain.auth.repository;

import com.mainichi.api.domain.auth.entity.RefreshToken;
import com.mainichi.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);

    /** 회원 탈퇴 시 FK(refresh_token.user_id) 때문에 유저보다 먼저 지운다. 한 번의 DELETE 쿼리로 처리. */
    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
