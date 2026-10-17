package com.mainichi.api.domain.quiz.service;

import com.mainichi.api.domain.quiz.entity.ListeningLine;
import com.mainichi.api.domain.quiz.entity.QuizQuestion;
import com.mainichi.api.domain.quiz.entity.QuizSourceCard;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** quiz 의 JSON 컬럼(TEXT) 변환. */
public final class QuizCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private QuizCodec() {}

    public static String encode(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static List<QuizQuestion> questions(String json) {
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    public static List<QuizSourceCard> sourceCards(String json) {
        return MAPPER.readValue(json, new TypeReference<>() {});
    }

    /** 독해(null)면 빈 목록 */
    public static List<ListeningLine> script(String json) {
        return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<>() {});
    }

    /** 아직 안 풀었으면(null) 빈 목록 */
    public static List<Integer> answers(String json) {
        return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<>() {});
    }
}
