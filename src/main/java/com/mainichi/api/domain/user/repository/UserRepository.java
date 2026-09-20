package com.mainichi.api.domain.user.repository;

import com.mainichi.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByProviderAndSocialId(String provider, String socialId);

}
