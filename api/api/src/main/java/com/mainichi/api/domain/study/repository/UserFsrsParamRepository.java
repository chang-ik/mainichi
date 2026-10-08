package com.mainichi.api.domain.study.repository;

import com.mainichi.api.domain.study.entity.UserFsrsParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserFsrsParamRepository extends JpaRepository<UserFsrsParam, UUID> {

    @Query("select p from UserFsrsParam p where p.user.id = :userId")
    Optional<UserFsrsParam> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from UserFsrsParam p where p.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
