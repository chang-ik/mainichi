package com.mainichi.api.domain.quiz.service;

import com.mainichi.api.domain.quiz.entity.ListeningLine.Speaker;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.exception.QuizBusyException;
import com.mainichi.api.domain.quiz.exception.QuizGenerationException;
import com.mainichi.api.domain.quiz.exception.QuizUnavailableException;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedListening;
import com.mainichi.api.domain.quiz.service.QuizGenerator.GeneratedReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Gemini generateContent 요청 형식과 응답·오류 처리 (가짜 서버) */
class GeminiQuizGeneratorTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<QuizSourceCard> CARDS =
            List.of(new QuizSourceCard("食[た]べる", "먹다", null, false));

    private MockRestServiceServer server;
    private GeminiQuizGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new GeminiQuizGenerator(builder, "test-key", "gemini-test");
    }

    private static Map<String, Object> question() {
        return Map.of("question", "質問[しつもん]", "choices", List.of("あ", "い", "う", "え"),
                "answerIndex", 2, "explanation", "해설");
    }

    /** 생각 파트 하나 + 답 파트(JSON 문자열) */
    private static String reply(Object answer, String finishReason) {
        return MAPPER.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", finishReason,
                "content", Map.of("role", "model", "parts", List.of(
                        Map.of("text", "생각 중…", "thought", true),
                        Map.of("text", MAPPER.writeValueAsString(answer))))))));
    }

    @Test
    @DisplayName("독해: 키 헤더·JSON 스키마·프롬프트를 보내고, 생각 파트를 뺀 JSON 을 읽는다")
    void reading() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.questions.minItems").value(2))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("読解")))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString("食[た]べる")))
                .andRespond(withSuccess(reply(Map.of(
                        "title", "題[だい]", "passage", "本文[ほんぶん]", "translation", "해석",
                        "questions", List.of(question(), question())), "STOP"), MediaType.APPLICATION_JSON));

        GeneratedReading r = generator.generateReading(4, CARDS);

        assertThat(r.title()).isEqualTo("題[だい]");
        assertThat(r.questions()).hasSize(2);
        assertThat(r.questions().get(0).answerIndex()).isEqualTo(2);
        assertThat(r.questions().get(0).choices()).containsExactly("あ", "い", "う", "え");
        server.verify();
    }

    @Test
    @DisplayName("청해: 화자(MALE/FEMALE)를 enum 으로 읽는다")
    void listening() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.lines.items.properties.speaker.enum[1]")
                        .value("FEMALE"))
                .andRespond(withSuccess(reply(Map.of(
                        "title", "会話[かいわ]", "situation", "二人[ふたり]が話[はな]しています。",
                        "lines", List.of(Map.of("speaker", "MALE", "text", "こんにちは。"),
                                Map.of("speaker", "FEMALE", "text", "こんにちは。")),
                        "translation", "남: 안녕하세요.\n여: 안녕하세요.",
                        "questions", List.of(question(), question())), "STOP"), MediaType.APPLICATION_JSON));

        GeneratedListening l = generator.generateListening(0, List.of());

        assertThat(l.lines()).extracting(line -> line.speaker()).containsExactly(Speaker.MALE, Speaker.FEMALE);
        assertThat(l.situation()).isEqualTo("二人[ふたり]が話[はな]しています。");
    }

    @Test
    @DisplayName("429(무료 사용량 한도)는 QuizBusyException")
    void rateLimited() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> generator.generateReading(5, CARDS)).isInstanceOf(QuizBusyException.class);
    }

    @Test
    @DisplayName("서버 오류, 토큰 한도로 잘림, 요청 차단, 깨진 JSON 은 QuizGenerationException")
    void failures() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> generator.generateReading(5, CARDS)).isInstanceOf(QuizGenerationException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(reply(Map.of("title", "x"), "MAX_TOKENS"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generateReading(5, CARDS)).isInstanceOf(QuizGenerationException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generateReading(5, CARDS)).isInstanceOf(QuizGenerationException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(MAPPER.writeValueAsString(Map.of("candidates", List.of(
                        Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", "{깨진"))))))),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> generator.generateReading(5, CARDS)).isInstanceOf(QuizGenerationException.class);
    }

    @Test
    @DisplayName("API 키가 없으면 요청을 보내지 않고 QuizUnavailableException")
    void noKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        GeminiQuizGenerator noKey = new GeminiQuizGenerator(builder, "", "gemini-test");

        assertThatThrownBy(() -> noKey.generateReading(5, CARDS)).isInstanceOf(QuizUnavailableException.class);
        noCalls.verify();
    }
}
