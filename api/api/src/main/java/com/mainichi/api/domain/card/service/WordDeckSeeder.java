package com.mainichi.api.domain.card.service;

import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.entity.Deck;
import com.mainichi.api.domain.card.entity.WordBack;
import com.mainichi.api.domain.card.repository.CardRepository;
import com.mainichi.api.domain.card.repository.DeckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 서버 시작 시 기본 단어·문법 덱(resources/seed/*.json)을 DB 에 넣는다.
 * 이미 있는 카드(같은 덱·같은 front)는 건너뛰므로 여러 번 실행해도 되고, JSON 에 카드를 추가하면 다음 시작 때 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seed.words.enabled", havingValue = "true", matchIfMissing = true)
public class WordDeckSeeder implements ApplicationRunner {

    static final List<String> SEED_FILES = List.of(
            "seed/kana-basic.json",
            "seed/words-n5.json",
            "seed/words-n4.json",
            "seed/words-n3.json",
            "seed/words-n2.json",
            "seed/words-n1.json",
            "seed/grammar-n5.json",
            "seed/grammar-n4.json",
            "seed/grammar-n3.json",
            "seed/grammar-n2.json",
            "seed/grammar-n1.json");

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;

    /** tag 를 생략하면 단어(word) 덱이다. */
    record SeedFile(String deck, int level, String tag, List<SeedWord> cards) {
        String cardTag() {
            return tag == null ? Card.TAG_WORD : tag;
        }
    }

    /** usage(접속 형태)는 문법 카드에만 있다. */
    record SeedWord(String front, String meaning, String example, String exampleMeaning, String usage) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        for (String path : SEED_FILES) {
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                seed(mapper.readValue(in, SeedFile.class));
            }
        }
    }

    void seed(SeedFile file) {
        Deck deck = deckRepository.findFirstByUserIsNullAndName(file.deck())
                .orElseGet(() -> deckRepository.save(Deck.builder().name(file.deck()).build()));

        Set<String> existing = new HashSet<>(cardRepository.findFrontsByDeck(deck));
        List<Card> toInsert = file.cards().stream()
                .filter(w -> existing.add(w.front()))
                .map(w -> Card.builder()
                        .deck(deck)
                        .cardTag(file.cardTag())
                        .level(file.level())
                        .front(w.front())
                        .back(WordBackCodec.encode(new WordBack(w.meaning(), w.example(), w.exampleMeaning(), w.usage())))
                        .build())
                .toList();

        if (!toInsert.isEmpty()) {
            cardRepository.saveAll(toInsert);
            log.info("덱 '{}'에 카드 {}장을 추가했습니다.", file.deck(), toInsert.size());
        }
    }
}
