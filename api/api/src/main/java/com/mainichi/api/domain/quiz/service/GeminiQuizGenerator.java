package com.mainichi.api.domain.quiz.service;

import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.exception.QuizBusyException;
import com.mainichi.api.domain.quiz.exception.QuizGenerationException;
import com.mainichi.api.domain.quiz.exception.QuizUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Google Gemini API(generateContent)로 JLPT 독해·청해 문제를 만든다. 기본 생성기 (quiz.generator=gemini).
 * 무료 등급은 분당·하루 요청 수 제한이 있어, 한도에 걸리면 QuizBusyException 으로 알린다.
 * API 키가 없으면 문제 만들기만 꺼진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "quiz.generator", havingValue = "gemini", matchIfMissing = true)
public class GeminiQuizGenerator implements QuizGenerator {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient client;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiQuizGenerator(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-3.8-flash}") String model
    ) {
        this(RestClient.builder().requestFactory(timeoutFactory()), apiKey, model);
    }

    /** 테스트에서 가짜 서버(MockRestServiceServer)를 붙일 수 있게 builder 를 받는다. */
    GeminiQuizGenerator(RestClient.Builder builder, String apiKey, String model) {
        this.client = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        // 생각하는 모델은 응답까지 1분 넘게 걸릴 수 있다
        factory.setReadTimeout(Duration.ofMinutes(3));
        return factory;
    }

    @Override
    public GeneratedReading generateReading(int level, List<QuizSourceCard> cards) {
        return call(GeneratedReading.class, READING_SCHEMA, QuizPrompts.READING, QuizPrompts.user(level, cards));
    }

    @Override
    public GeneratedListening generateListening(int level, List<QuizSourceCard> cards) {
        return call(GeneratedListening.class, LISTENING_SCHEMA, QuizPrompts.LISTENING, QuizPrompts.user(level, cards));
    }

    private <T> T call(Class<T> type, Map<String, Object> schema, String system, String user) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new QuizUnavailableException();
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", schema,
                        // 생각(thinking) 토큰도 여기에 포함된다
                        "maxOutputTokens", 16384));

        GeminiResponse response;
        try {
            response = client.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.info("Gemini 사용량 한도 ({})", type.getSimpleName());
                throw new QuizBusyException();
            }
            log.warn("Gemini 요청 실패 ({}): {} {}", type.getSimpleName(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new QuizGenerationException();
        } catch (ResourceAccessException e) {
            log.warn("Gemini 연결 실패 ({})", type.getSimpleName(), e);
            throw new QuizGenerationException();
        }

        String json = textOf(response, type);
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            log.warn("Gemini 응답을 읽지 못함 ({}): {}", type.getSimpleName(), e.getOriginalMessage());
            throw new QuizGenerationException();
        }
    }

    /** 첫 후보의 답 글자 (생각 파트는 뺀다). 막혔거나 끝까지 못 썼으면 실패로 본다. */
    private static String textOf(GeminiResponse response, Class<?> type) {
        if (response == null) throw new QuizGenerationException();
        if (response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
            log.warn("Gemini 가 요청을 막음 ({}): {}", type.getSimpleName(), response.promptFeedback().blockReason());
            throw new QuizGenerationException();
        }
        Candidate candidate = response.candidates() == null || response.candidates().isEmpty()
                ? null : response.candidates().get(0);
        if (candidate == null || !"STOP".equals(candidate.finishReason())
                || candidate.content() == null || candidate.content().parts() == null) {
            log.warn("Gemini 생성이 끝까지 되지 않음 ({}): finishReason={}", type.getSimpleName(),
                    candidate == null ? null : candidate.finishReason());
            throw new QuizGenerationException();
        }
        String text = candidate.content().parts().stream()
                .filter(p -> !Boolean.TRUE.equals(p.thought()))
                .map(Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        if (text.isBlank()) throw new QuizGenerationException();
        return text;
    }

    // ---------------------------------------------------------------- 응답 형식 (필요한 필드만)

    record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {}

    record Candidate(Content content, String finishReason) {}

    record Content(List<Part> parts) {}

    record Part(String text, Boolean thought) {}

    record PromptFeedback(String blockReason) {}

    // ---------------------------------------------------------------- 출력 스키마 (OpenAPI 부분집합, QuizGenerator 의 record 와 같은 모양)

    private static Map<String, Object> string(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> order) {
        return Map.of("type", "OBJECT", "properties", properties, "required", order, "propertyOrdering", order);
    }

    private static final Map<String, Object> QUESTION = object(Map.of(
                    "question", string("질문. 일본어, 후리가나 대괄호 표기"),
                    "choices", Map.of("type", "ARRAY", "items", string("보기. 일본어, 후리가나 대괄호 표기, 번호 없이"),
                            "minItems", 4, "maxItems", 4, "description", "보기 정확히 4개"),
                    "answerIndex", Map.of("type", "INTEGER", "description", "정답 보기의 0부터 시작하는 번호 (0~3)"),
                    "explanation", string("정답인 이유와 지문/대화 속 근거를 한국어로 2~3문장")),
            List.of("question", "choices", "answerIndex", "explanation"));

    private static final Map<String, Object> QUESTIONS = Map.of(
            "type", "ARRAY", "items", QUESTION, "minItems", 2, "maxItems", 2, "description", "문제 정확히 2개");

    static final Map<String, Object> READING_SCHEMA = object(Map.of(
                    "title", string("지문 제목. 일본어, 후리가나 대괄호 표기"),
                    "passage", string("지문. 일본어, 후리가나 대괄호 표기, 문단은 빈 줄로 구분"),
                    "translation", string("지문 전체의 자연스러운 한국어 해석"),
                    "questions", QUESTIONS),
            List.of("title", "passage", "translation", "questions"));

    static final Map<String, Object> LISTENING_SCHEMA = object(Map.of(
                    "title", string("화면에 보일 짧은 제목. 일본어, 후리가나 대괄호 표기"),
                    "situation", string("대화 전에 읽어 주는 상황 설명 한 문장. 일본어, 후리가나 대괄호 표기"),
                    "lines", Map.of("type", "ARRAY", "description", "대화. 남자(MALE)와 여자(FEMALE)가 번갈아 말한다",
                            "items", object(Map.of(
                                            "speaker", Map.of("type", "STRING", "enum", List.of("MALE", "FEMALE"),
                                                    "description", "말하는 사람"),
                                            "text", string("대사. 일본어, 후리가나 대괄호 표기")),
                                    List.of("speaker", "text"))),
                    "translation", string("상황 설명과 대화의 한국어 해석. 한 줄에 한 대사씩 '남: …', '여: …'"),
                    "questions", QUESTIONS),
            List.of("title", "situation", "lines", "translation", "questions"));
}
