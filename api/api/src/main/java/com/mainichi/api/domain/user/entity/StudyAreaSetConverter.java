package com.mainichi.api.domain.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 학습 영역 집합을 "GRAMMAR,LISTENING" 같은 문자열로 저장한다. 순서는 enum 선언 순서로 고정. */
@Converter
public class StudyAreaSetConverter implements AttributeConverter<Set<StudyArea>, String> {

    @Override
    public String convertToDatabaseColumn(Set<StudyArea> areas) {
        if (areas == null || areas.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(areas).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<StudyArea> convertToEntityAttribute(String value) {
        EnumSet<StudyArea> areas = EnumSet.noneOf(StudyArea.class);
        if (value == null || value.isBlank()) {
            return areas;
        }
        // 없어진 영역(예: 예전에 고를 수 있던 CONVERSATION)은 건너뛴다.
        Arrays.stream(value.split(","))
                .map(String::trim)
                .flatMap(name -> Arrays.stream(StudyArea.values()).filter(a -> a.name().equals(name)))
                .forEach(areas::add);
        return areas;
    }
}
