package com.mainichi.api.domain.stats;

import com.jayway.jsonpath.JsonPath;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.repository.UserRepository;
import com.mainichi.api.global.common.JwtTokenProvider;
import com.mainichi.api.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단어 학습 기록이 Anki 식 통계(오늘·연속일·달력·예정·카드 수·버튼·시간대)로 잡히는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
class StatsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        clock.reset();
        User user = userRepository.save(User.builder().provider("kakao").socialId(UUID.randomUUID().toString()).build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    /** 새 단어 카드 한 장을 rating 으로 평가 (5초 걸린 것으로) */
    private void learnWord(int rating) throws Exception {
        String body = mockMvc.perform(get("/study/words").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        String cardId = JsonPath.read(body, "$.card.id");
        mockMvc.perform(post("/study/words/{id}/review", cardId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": " + rating + ", \"durationMs\": 5000}"))
                .andExpect(status().isOk());
    }

    private ResultActions stats(String query) throws Exception {
        return mockMvc.perform(get("/stats" + query).header("Authorization", bearer));
    }

    @Test
    @DisplayName("오늘 복습 수·시간·정답률, 카드 수, 미래 예정, 버튼·시간대 통계")
    void todayAndCards() throws Exception {
        learnWord(4); // 쉬움 → 바로 복습 단계 (16일 뒤)
        learnWord(3); // 알맞음 → 학습 중 (10분 뒤)
        learnWord(1); // 다시 → 학습 중 (1분 뒤)

        stats("?deck=words&days=30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-09-29"))
                .andExpect(jsonPath("$.todayStats.reviews").value(3))
                .andExpect(jsonPath("$.todayStats.seconds").value(15))
                .andExpect(jsonPath("$.todayStats.newCards").value(3))
                .andExpect(jsonPath("$.todayStats.again").value(1))
                .andExpect(jsonPath("$.todayStats.correctRate").value(66.7))
                .andExpect(jsonPath("$.streak.current").value(1))
                .andExpect(jsonPath("$.calendar.length()").value(1))
                .andExpect(jsonPath("$.calendar[0].count").value(3))
                .andExpect(jsonPath("$.reviews.length()").value(30))
                .andExpect(jsonPath("$.reviews[29].date").value("2026-09-29"))
                .andExpect(jsonPath("$.reviews[29].learning").value(3))
                .andExpect(jsonPath("$.reviews[29].newCards").value(3))
                .andExpect(jsonPath("$.reviewSummary.total").value(3))
                .andExpect(jsonPath("$.reviewSummary.daysStudied").value(1))
                // 기초 단어(가나) 덱 142장 중 3장을 봤다
                .andExpect(jsonPath("$.cardCounts.newCards").value(139))
                .andExpect(jsonPath("$.cardCounts.learning").value(2))
                .andExpect(jsonPath("$.cardCounts.young").value(1))
                .andExpect(jsonPath("$.cardCounts.mature").value(0))
                .andExpect(jsonPath("$.forecast.due.length()").value(30))
                .andExpect(jsonPath("$.forecast.due[0]").value(2))
                .andExpect(jsonPath("$.forecast.due[16]").value(1))
                .andExpect(jsonPath("$.intervals.average").value(16.0))
                .andExpect(jsonPath("$.retrievability.buckets.length()").value(6))
                .andExpect(jsonPath("$.hours[9].count").value(3))
                .andExpect(jsonPath("$.buttons[0].group").value("LEARNING"))
                .andExpect(jsonPath("$.buttons[0].counts[0]").value(1))
                .andExpect(jsonPath("$.buttons[0].counts[2]").value(1))
                .andExpect(jsonPath("$.buttons[0].counts[3]").value(1))
                .andExpect(jsonPath("$.retention.length()").value(5));

        // 문법만 보면 기록이 없다
        stats("?deck=grammar")
                .andExpect(jsonPath("$.todayStats.reviews").value(0))
                .andExpect(jsonPath("$.todayStats.correctRate").doesNotExist())
                .andExpect(jsonPath("$.cardCounts.newCards").value(31));
    }

    @Test
    @DisplayName("연속 학습일: 오늘 안 했으면 어제까지 이어지고, 하루를 건너뛰면 끊긴다")
    void streak() throws Exception {
        learnWord(4);
        clock.advance(Duration.ofDays(1));
        learnWord(4);

        stats("").andExpect(jsonPath("$.streak.current").value(2))
                .andExpect(jsonPath("$.streak.longest").value(2))
                .andExpect(jsonPath("$.calendar.length()").value(2));

        clock.advance(Duration.ofDays(1));
        stats("").andExpect(jsonPath("$.streak.current").value(2));

        clock.advance(Duration.ofDays(1));
        stats("").andExpect(jsonPath("$.streak.current").value(0))
                .andExpect(jsonPath("$.streak.longest").value(2))
                .andExpect(jsonPath("$.streak.studiedDays").value(2));
    }

    @Test
    @DisplayName("deck 과 days 가 정해진 값이 아니면 400")
    void invalidQuery() throws Exception {
        stats("?deck=kanji").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        stats("?days=7").andExpect(status().isBadRequest());
        stats("?days=abc").andExpect(status().isBadRequest());
        stats("?days=365").andExpect(status().isOk()).andExpect(jsonPath("$.reviews.length()").value(365));
    }
}
