package com.mainichi.api.domain.study.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 숫자 배열을 JSON 컬럼 문자열(예: [1.0,10.0])로 저장한다. 숫자만 다루므로 JSON 라이브러리 없이 처리. */
@Converter
public class JsonNumberListConverter implements AttributeConverter<List<Double>, String> {

    @Override
    public String convertToDatabaseColumn(List<Double> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public List<Double> convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        }
        String body = json.trim();
        // H2(테스트 DB)는 문자열 파라미터를 JSON 문자열 값으로 저장해 "\"[...]\"" 형태로 돌려준다.
        if (body.length() >= 2 && body.startsWith("\"") && body.endsWith("\"")) {
            body = body.substring(1, body.length() - 1).trim();
        }
        if (!body.startsWith("[") || !body.endsWith("]")) {
            throw new IllegalArgumentException("not a JSON number array: " + json);
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(body.split(",")).map(String::trim).map(Double::valueOf).toList();
    }
}
