package com.mainichi.api.domain.quiz.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import com.mainichi.api.domain.quiz.exception.QuizGenerationException;
import com.mainichi.api.domain.quiz.exception.QuizUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Claude API 로 JLPT 독해·청해 문제를 만든다 (유료). quiz.generator=claude 일 때만 쓴다.
 * API 키가 없으면 문제 만들기만 꺼진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "quiz.generator", havingValue = "claude")
public class ClaudeQuizGenerator implements QuizGenerator {

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public ClaudeQuizGenerator(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:claude-opus-5-5}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public GeneratedReading generateReading(int level, List<QuizSourceCard> cards) {
        return call(GeneratedReading.class, QuizPrompts.READING, QuizPrompts.user(level, cards));
    }

    @Override
    public GeneratedListening generateListening(int level, List<QuizSourceCard> cards) {
        return call(GeneratedListening.class, QuizPrompts.LISTENING, QuizPrompts.user(level, cards));
    }

    private <T> T call(Class<T> type, String system, String user) {
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(type)
                .addUserMessage(user)
                // 안전 분류기가 거절하면 서버가 다른 모델로 다시 시도한다
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        // outputConfig(Class) 가 만든 JSON 스키마에 effort 를 더한다 (Opus 5.5 기본값도 medium 이지만 명시)
        var format = builder.build().rawParams().outputConfig().orElseThrow().format().orElseThrow();
        builder.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).format(format).build());

        StructuredMessage<T> response;
        try {
            response = client().messages().create(builder.build());
        } catch (AnthropicException e) {
            log.warn("문제 생성 요청 실패 ({})", type.getSimpleName(), e);
            throw new QuizGenerationException();
        }

        StopReason stop = response.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            log.warn("문제 생성이 끝까지 되지 않음 ({}): stopReason={}, stopDetails={}",
                    type.getSimpleName(), stop, response.stopDetails());
            throw new QuizGenerationException();
        }
        try {
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(StructuredTextBlock::text)
                    .findFirst()
                    .orElseThrow(QuizGenerationException::new);
        } catch (AnthropicException e) {
            log.warn("문제 응답을 읽지 못함 ({})", type.getSimpleName(), e);
            throw new QuizGenerationException();
        }
    }

    private AnthropicClient client() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new QuizUnavailableException();
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(Duration.ofMinutes(3))
                            .build();
                }
            }
        }
        return client;
    }
}
