package com.mainichi.api.domain.card.repository;

import com.mainichi.api.domain.card.entity.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    /** 서비스 기본 덱(user_id 가 NULL)을 이름으로 찾는다. */
    Optional<Deck> findFirstByUserIsNullAndName(String name);
}
