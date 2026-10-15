package com.mainichi.api.domain.quiz;

import com.jayway.jsonpath.JsonPath;
import com.mainichi.api.domain.quiz.entity.ListeningLine;
import com.mainichi.api.domain.quiz.entity.ListeningLine.Speaker;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.service.QuizGenerator;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedListening;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedQuestion;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedReading;
import com.mainichi.api.domain.user.entity.User;
import com.mainichi.api.domain.user.repository.UserRepository;
import com.mainichi.api.global.common.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 단어 학습 → 오늘 배운 카드로 독해·청해 문제 생성 → 채점 흐름. Claude 호출은 가짜로 바꾼다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockitoBean private QuizGenerator quizGenerator;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().provider("kakao").socialId(UUID.randomUUID().toString()).build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
        when(quizGenerator.generateReading(anyInt(), anyList())).thenReturn(reading(4));
        when(quizGenerator.generateListening(anyInt(), anyList())).thenReturn(listening(List.of(
                new ListeningLine(Speaker.MALE, "あした えいがに いきませんか。"),
                new ListeningLine(Speaker.FEMALE, "いいですね。いきましょう。"))));
    }

    private static List<GeneratedQuestion> questions(int choices) {
        List<String> options = List.of("ひとつめ", "ふたつめ", "みっつめ", "よっつめ").subList(0, choices);
        return List.of(
                new GeneratedQuestion("てんきは どうでしたか。", options, 1, "본문 첫 문장에 비라고 나온다."),
                new GeneratedQuestion("いいたいことは なんですか。", options, 3, "마지막 문장이 근거다."));
    }

    private static GeneratedReading reading(int choices) {
        return new GeneratedReading("雨[あめ]の日[ひ]", "今日[きょう]は雨[あめ]でした。", "오늘은 비가 왔습니다.", questions(choices));
    }

    private static GeneratedListening listening(List<ListeningLine> lines) {
        return new GeneratedListening("えいが", "おとこのひとと おんなのひとが はなしています。", lines,
                "남: 내일 영화 보러 가지 않을래요?\n여: 좋아요. 가요.", questions(4));
    }

    /** 단어 카드를 count 장 처음 학습한다 */
    private void learnWords(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            String body = mockMvc.perform(get("/study/words").header("Authorization", bearer))
                    .andReturn().getResponse().getContentAsString();
            String cardId = JsonPath.read(body, "$.card.id");
            mockMvc.perform(post("/study/words/{id}/review", cardId)
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\": 4}"))
                    .andExpect(status().isOk());
        }
    }

    private ResultActions today(String kind) throws Exception {
        return mockMvc.perform(get("/quiz/{kind}/today", kind).header("Authorization", bearer));
    }

    private ResultActions create(String kind) throws Exception {
        return mockMvc.perform(post("/quiz/{kind}", kind).header("Authorization", bearer));
    }

    private ResultActions submit(String kind, String quizId, String answers) throws Exception {
        return mockMvc.perform(post("/quiz/{kind}/{id}/answers", kind, quizId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": " + answers + "}"));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ---------------------------------------------------------------- 독해

    @Test
    @DisplayName("독해: 오늘 새로 배운 카드가 3장보다 적으면 문제를 만들지 않는다")
    void readingNeedsCards() throws Exception {
        learnWords(2);

        today("reading")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCardCount").value(2))
                .andExpect(jsonPath("$.minNewCards").value(3))
                .andExpect(jsonPath("$.quiz").value(nullValue()));

        create("reading")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_NEW_CARDS"));
        verify(quizGenerator, never()).generateReading(anyInt(), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("독해: 오늘 배운 카드로 문제를 만들고, 풀기 전에는 정답을 숨긴다. 안 푼 문제가 있으면 새로 만들지 않는다")
    void readingCreateHidesAnswers() throws Exception {
        learnWords(3);

        String quizId = id(create("reading")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("READING"))
                .andExpect(jsonPath("$.title").value("雨[あめ]の日[ひ]"))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.script.length()").value(0))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].choices.length()").value(4))
                .andExpect(jsonPath("$.questions[0].answerIndex").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].explanation").value(nullValue()))
                .andExpect(jsonPath("$.translation").value(nullValue()))
                .andExpect(jsonPath("$.sourceCards.length()").value(3))
                .andExpect(jsonPath("$.submitted").value(false)));

        ArgumentCaptor<List<QuizSourceCard>> cards = ArgumentCaptor.forClass(List.class);
        verify(quizGenerator).generateReading(eq(0), cards.capture());
        assertThat(cards.getValue()).hasSize(3).allSatisfy(c -> {
            assertThat(c.front()).isNotBlank();
            assertThat(c.meaning()).isNotBlank();
            assertThat(c.grammar()).isFalse();
        });

        assertThat(id(create("reading").andExpect(status().isOk()))).isEqualTo(quizId);
        verify(quizGenerator, times(1)).generateReading(anyInt(), anyList());

        today("reading")
                .andExpect(jsonPath("$.quiz.id").value(quizId))
                .andExpect(jsonPath("$.remainingToday").value(2));
    }

    @Test
    @DisplayName("답을 내면 채점 결과와 정답·해설·해석을 받고, 다시 내도 결과는 바뀌지 않는다")
    void submitGrades() throws Exception {
        learnWords(3);
        String quizId = id(create("reading"));

        submit("reading", quizId, "[1, 0]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.translation").value("오늘은 비가 왔습니다."))
                .andExpect(jsonPath("$.questions[0].selectedIndex").value(1))
                .andExpect(jsonPath("$.questions[0].answerIndex").value(1))
                .andExpect(jsonPath("$.questions[1].selectedIndex").value(0))
                .andExpect(jsonPath("$.questions[1].answerIndex").value(3))
                .andExpect(jsonPath("$.questions[1].explanation").isNotEmpty());

        submit("reading", quizId, "[1, 3]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(1));

        // 같은 문제를 다시 내도 한 번으로 센다
        today("reading").andExpect(jsonPath("$.solvedToday").value(1));
        today("listening").andExpect(jsonPath("$.solvedToday").value(0));
    }

    @Test
    @DisplayName("다 풀면 새 문제를 만들 수 있고, 종류별로 하루 3개까지만 만든다")
    void dailyLimitPerKind() throws Exception {
        learnWords(3);
        for (int i = 0; i < 3; i++) {
            submit("reading", id(create("reading").andExpect(status().isOk())), "[0, 0]").andExpect(status().isOk());
        }

        create("reading")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("QUIZ_DAILY_LIMIT"));
        // 청해는 따로 센다
        create("listening").andExpect(status().isOk());
    }

    @Test
    @DisplayName("모델 응답 형식이 맞지 않으면(보기 3개) 저장하지 않고 502 를 준다")
    void invalidGeneration() throws Exception {
        learnWords(3);
        when(quizGenerator.generateReading(anyInt(), anyList())).thenReturn(reading(3));

        create("reading")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("QUIZ_GENERATION_FAILED"));
        today("reading").andExpect(jsonPath("$.quiz").value(nullValue()));
    }

    @Test
    @DisplayName("첫 응답이 형식에 안 맞으면 한 번 더 요청해서 만든다")
    void retriesOnce() throws Exception {
        learnWords(3);
        when(quizGenerator.generateReading(anyInt(), anyList())).thenReturn(reading(3), reading(4));

        create("reading")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].choices.length()").value(4));
        verify(quizGenerator, times(2)).generateReading(anyInt(), anyList());
    }

    // ---------------------------------------------------------------- 청해

    @Test
    @DisplayName("청해: 오늘 배운 카드가 없어도 단계에 맞게 만들고, 대화(script)는 소리로 읽도록 풀기 전에도 준다")
    void listeningWithoutCards() throws Exception {
        today("listening")
                .andExpect(jsonPath("$.minNewCards").value(0))
                .andExpect(jsonPath("$.remainingToday").value(3));

        String quizId = id(create("listening")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("LISTENING"))
                .andExpect(jsonPath("$.passage").value("おとこのひとと おんなのひとが はなしています。"))
                .andExpect(jsonPath("$.script.length()").value(2))
                .andExpect(jsonPath("$.script[0].speaker").value("MALE"))
                .andExpect(jsonPath("$.script[1].speaker").value("FEMALE"))
                .andExpect(jsonPath("$.translation").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].answerIndex").value(nullValue())));
        verify(quizGenerator).generateListening(eq(0), eq(List.of()));

        submit("listening", quizId, "[1, 3]")
                .andExpect(jsonPath("$.correctCount").value(2))
                .andExpect(jsonPath("$.translation").isNotEmpty());

        // 다른 종류 경로로는 찾을 수 없다
        submit("reading", quizId, "[1, 3]").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("청해: 학습 단계와 오늘 배운 카드를 넘기고, 대사가 1줄뿐이면 502")
    void listeningUsesLevelAndCards() throws Exception {
        learnWords(2);
        String first = id(create("listening").andExpect(status().isOk()));
        verify(quizGenerator).generateListening(eq(0), argThat(l -> l.size() == 2));
        submit("listening", first, "[0, 0]").andExpect(status().isOk());

        when(quizGenerator.generateListening(anyInt(), anyList()))
                .thenReturn(listening(List.of(new ListeningLine(Speaker.MALE, "こんにちは。"))));
        create("listening")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("QUIZ_GENERATION_FAILED"));
    }

    // ---------------------------------------------------------------- 오류

    @Test
    @DisplayName("잘못된 답·종류, 남의 문제, 없는 문제는 거절한다. 문제가 있어도 회원 탈퇴가 된다")
    void errors() throws Exception {
        learnWords(3);
        String quizId = id(create("reading"));

        submit("reading", quizId, "[1]").andExpect(status().isBadRequest());
        submit("reading", quizId, "[1, 4]").andExpect(status().isBadRequest());
        submit("reading", UUID.randomUUID().toString(), "[0, 0]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));
        create("speaking")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUIZ_KIND_NOT_FOUND"));

        User other = userRepository.save(User.builder().provider("kakao").socialId(UUID.randomUUID().toString()).build());
        mockMvc.perform(post("/quiz/reading/{id}/answers", quizId)
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(other.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [0, 0]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/user/me").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
