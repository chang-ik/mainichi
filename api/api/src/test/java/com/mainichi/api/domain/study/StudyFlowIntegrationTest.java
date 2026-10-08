package com.mainichi.api.domain.study;

import com.jayway.jsonpath.JsonPath;
import com.mainichi.api.domain.card.entity.Card;
import com.mainichi.api.domain.card.repository.CardRepository;
import com.mainichi.api.domain.card.service.WordDeckSeeder;
import com.mainichi.api.domain.study.repository.CardStateRepository;
import com.mainichi.api.domain.study.repository.ReviewLogRepository;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.repository.UserRepository;
import com.mainichi.api.global.common.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 스프링 컨텍스트 + H2 로 인증 → 학습 큐 → 평가 → 하루 한도 → 탈퇴까지 흐름을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StudyFlowIntegrationTest.MutableClockConfig.class)
class StudyFlowIntegrationTest {

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-29T00:00:00Z"); // 서울 09:00
        private final ZoneId zone = ZoneId.of("Asia/Seoul");

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        void reset() {
            instant = Instant.parse("2026-09-29T00:00:00Z");
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository userRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardStateRepository cardStateRepository;
    @Autowired private ReviewLogRepository reviewLogRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private WordDeckSeeder wordDeckSeeder;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        clock.reset();
        user = userRepository.save(User.builder().provider("kakao").socialId(UUID.randomUUID().toString()).build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private ResultActions queue() throws Exception {
        return mockMvc.perform(get("/study/words").header("Authorization", bearer));
    }

    private ResultActions review(String cardId, int rating) throws Exception {
        return mockMvc.perform(post("/study/words/{id}/review", cardId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": " + rating + "}"));
    }

    private static String cardId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.card.id");
    }

    @Test
    @DisplayName("처음 들어오면 오늘의 새 카드 20장과 버튼별 간격 안내를 받는다")
    void firstQueue() throws Exception {
        queue()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.newCards").value(20))
                .andExpect(jsonPath("$.counts.learning").value(0))
                .andExpect(jsonPath("$.counts.review").value(0))
                .andExpect(jsonPath("$.card.phase").value("NEW"))
                .andExpect(jsonPath("$.card.front").isNotEmpty())
                .andExpect(jsonPath("$.card.meaning").isNotEmpty())
                .andExpect(jsonPath("$.card.intervals.again").value(60))
                .andExpect(jsonPath("$.card.intervals.hard").value(330))
                .andExpect(jsonPath("$.card.intervals.good").value(600))
                .andExpect(jsonPath("$.card.intervals.easy").value(16 * 86400));
    }

    @Test
    @DisplayName("○ 를 누른 카드는 10분 뒤에 학습 중 카드로 다시 나온다")
    void learningCardComesBack() throws Exception {
        String first = cardId(queue());

        ResultActions afterReview = review(first, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.newCards").value(19))
                .andExpect(jsonPath("$.counts.learning").value(0))
                .andExpect(jsonPath("$.card.id").value(not(first)));
        String second = cardId(afterReview);
        assertThat(second).isNotEqualTo(first);

        clock.advance(Duration.ofMinutes(11));

        queue()
                .andExpect(jsonPath("$.counts.learning").value(1))
                .andExpect(jsonPath("$.card.id").value(first))
                .andExpect(jsonPath("$.card.phase").value("LEARNING"));
    }

    @Test
    @DisplayName("새 카드 하루 한도를 채우면 새 카드가 더 나오지 않고, 20분 안의 학습 카드를 미리 보여준다")
    void dailyLimitAndLearnAhead() throws Exception {
        String first = cardId(queue());
        ResultActions last = review(first, 3); // 10분 뒤 다시

        for (int i = 0; i < 19; i++) {
            last = review(cardId(last), 4); // 나머지는 쉬움 → 16일 뒤
        }

        // 새 카드 20장을 다 봤고, 10분 뒤 돌아올 첫 카드를 미리 보여준다
        last.andExpect(jsonPath("$.counts.newCards").value(0))
                .andExpect(jsonPath("$.counts.learning").value(1))
                .andExpect(jsonPath("$.card.id").value(first));

        review(first, 3)
                .andExpect(jsonPath("$.card").value(nullValue()))
                .andExpect(jsonPath("$.nextDueInSeconds").value(nullValue()))
                .andExpect(jsonPath("$.counts.newCards").value(0));

        assertThat(reviewLogRepository.count()).isGreaterThanOrEqualTo(21);
    }

    @Test
    @DisplayName("다음 날이 되면 쉬움으로 넘긴 카드는 아직 안 나오고, 새 카드 한도는 다시 20장이 된다")
    void nextDayResetsNewLimit() throws Exception {
        ResultActions last = queue();
        for (int i = 0; i < 20; i++) {
            last = review(cardId(last), 4);
        }
        last.andExpect(jsonPath("$.counts.newCards").value(0));

        clock.advance(Duration.ofDays(1));

        queue()
                .andExpect(jsonPath("$.counts.newCards").value(20))
                .andExpect(jsonPath("$.counts.review").value(0));

        clock.advance(Duration.ofDays(16));

        queue()
                .andExpect(jsonPath("$.counts.review").value(20))
                .andExpect(jsonPath("$.card.phase").value("REVIEW"));
    }

    @Test
    @DisplayName("복습 카드에서 ✕ 를 누르면 재학습 카드가 되어 10분 뒤 가장 먼저 나오고 lapses 가 오른다")
    void lapseGoesToRelearning() throws Exception {
        ResultActions last = queue();
        for (int i = 0; i < 20; i++) {
            last = review(cardId(last), 4);
        }

        clock.advance(Duration.ofDays(17));
        String lapsed = cardId(queue().andExpect(jsonPath("$.card.phase").value("REVIEW")));
        review(lapsed, 1).andExpect(jsonPath("$.card.id").value(not(lapsed)));

        clock.advance(Duration.ofMinutes(11));

        queue()
                .andExpect(jsonPath("$.card.id").value(lapsed))
                .andExpect(jsonPath("$.card.phase").value("RELEARNING"))
                .andExpect(jsonPath("$.counts.learning").value(1));
        assertThat(cardStateRepository.findByUserIdAndCardId(user.getId(), UUID.fromString(lapsed)))
                .hasValueSatisfying(s -> assertThat(s.getLapses()).isEqualTo(1));
    }

    @Test
    @DisplayName("잘못된 평가 값은 400, 없는 카드는 404, 토큰이 없으면 403")
    void errors() throws Exception {
        String first = cardId(queue());

        review(first, 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        review(UUID.randomUUID().toString(), 3)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CARD_NOT_FOUND"));

        mockMvc.perform(get("/study/words")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("학습 기록이 있어도 회원 탈퇴가 FK 제약 없이 된다")
    void deleteAccountWithStudyData() throws Exception {
        review(cardId(queue()), 3).andExpect(status().isOk());

        mockMvc.perform(delete("/user/me").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(cardStateRepository.findAll()).noneMatch(s -> s.getUser().getId().equals(user.getId()));
    }

    @Test
    @DisplayName("학습 단계를 바꾸면 그 단계 덱의 카드가 나온다 (0=기초 가나, 3=N3)")
    void levelSelectsDeck() throws Exception {
        assertThat(cardLevel(cardId(queue()))).isZero();

        mockMvc.perform(patch("/user/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentLevel\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLevel").value(3));

        assertThat(cardLevel(cardId(queue()))).isEqualTo(3);
    }

    @Test
    @DisplayName("하루 학습량을 바꾸면 오늘 새 카드 수가 그만큼으로 바뀐다")
    void dailyAmountLimitsNewCards() throws Exception {
        mockMvc.perform(get("/study/settings").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCardsPerDay").value(20))
                .andExpect(jsonPath("$.defaultNewCardsPerDay").value(20));

        mockMvc.perform(patch("/study/settings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCardsPerDay\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCardsPerDay").value(5));

        queue().andExpect(jsonPath("$.counts.newCards").value(5));
    }

    @Test
    @DisplayName("하루 학습량이 1~100 밖이면 400")
    void dailyAmountOutOfRange() throws Exception {
        mockMvc.perform(patch("/study/settings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCardsPerDay\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private void setLevel(int level) throws Exception {
        mockMvc.perform(patch("/user/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentLevel\": " + level + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions grammarQueue() throws Exception {
        return mockMvc.perform(get("/study/grammar").header("Authorization", bearer));
    }

    @Test
    @DisplayName("문법도 단계에 맞는 카드로 나오고, 접속 형태(usage)가 함께 온다")
    void grammarQueueByLevel() throws Exception {
        setLevel(5);

        String id = cardId(grammarQueue()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.newCards").value(20))
                .andExpect(jsonPath("$.card.phase").value("NEW"))
                .andExpect(jsonPath("$.card.usage").isNotEmpty())
                .andExpect(jsonPath("$.card.example").isNotEmpty()));

        Card card = cardRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(card.getCardTag()).isEqualTo(Card.TAG_GRAMMAR);
        assertThat(card.getLevel()).isEqualTo(5);
    }

    @Test
    @DisplayName("단어 카드에는 usage 가 없다")
    void wordCardHasNoUsage() throws Exception {
        queue().andExpect(jsonPath("$.card.usage").value(nullValue()));
    }

    @Test
    @DisplayName("문법과 단어는 하루 새 카드 수를 따로 센다")
    void grammarAndWordLimitsAreSeparate() throws Exception {
        setLevel(5);
        mockMvc.perform(patch("/study/settings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCardsPerDay\": 1}"))
                .andExpect(status().isOk());

        String grammarCard = cardId(grammarQueue());
        mockMvc.perform(post("/study/grammar/{id}/review", grammarCard)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.newCards").value(0));

        queue().andExpect(jsonPath("$.counts.newCards").value(1));
    }

    @Test
    @DisplayName("기초 단계에는 문법 카드가 없다")
    void noGrammarForBasic() throws Exception {
        grammarQueue()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.newCards").value(0))
                .andExpect(jsonPath("$.card").value(nullValue()));
    }

    @Test
    @DisplayName("문법 카드를 단어 경로로 평가하면 404, 없는 학습 종류도 404")
    void wrongKind() throws Exception {
        setLevel(5);
        String grammarCard = cardId(grammarQueue());

        review(grammarCard, 3)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CARD_NOT_FOUND"));

        mockMvc.perform(get("/study/music").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDY_KIND_NOT_FOUND"));
    }

    private int cardLevel(String cardId) {
        return cardRepository.findById(UUID.fromString(cardId)).orElseThrow().getLevel();
    }

    @Test
    @DisplayName("시드는 여러 번 실행해도 카드가 중복으로 들어가지 않는다")
    void seederIsIdempotent() throws Exception {
        long before = cardRepository.count();

        wordDeckSeeder.run(null);

        assertThat(cardRepository.count()).isEqualTo(before).isEqualTo(142 + 100 * 5 + (31 + 34 + 32 + 33 + 32));
    }
}
