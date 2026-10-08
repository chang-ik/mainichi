package com.mainichi.api.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 단어·문법 카드 뒷면(card.back)에 JSON 으로 저장하는 내용.
 * 읽는 법은 front 의 대괄호 후리가나에서 뽑을 수 있으므로 따로 저장하지 않는다.
 *
 * @param meaning         뜻 (한국어)
 * @param example         예문 (후리가나 대괄호 표기)
 * @param exampleMeaning  예문 해석
 * @param usage           문법 카드의 접속 형태 (예: 동사 て형 + もいい). 단어 카드는 null
 */
// 단어 카드는 usage 가 없으므로 JSON 에 넣지 않는다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WordBack(String meaning, String example, String exampleMeaning, String usage) {

    public WordBack(String meaning, String example, String exampleMeaning) {
        this(meaning, example, exampleMeaning, null);
    }
}
