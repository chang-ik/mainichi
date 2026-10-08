package com.mainichi.api.domain.card.service;

import com.mainichi.api.domain.card.entity.WordBack;
import tools.jackson.databind.json.JsonMapper;

/** card.back(TEXT) 과 WordBack 사이 JSON 변환. */
public final class WordBackCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private WordBackCodec() {}

    public static String encode(WordBack back) {
        return MAPPER.writeValueAsString(back);
    }

    public static WordBack decode(String json) {
        return MAPPER.readValue(json, WordBack.class);
    }
}
