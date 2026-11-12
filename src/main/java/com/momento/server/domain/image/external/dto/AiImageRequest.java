package com.momento.server.domain.image.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;

/**
 * AI 서버 이미지 생성 요청. 값 목록은 AI 서버의 {@code GET /styles} 기준이다.
 *
 * @param letter 편지 본문. 제목은 AI 쪽이 아직 쓰지 않아 보내지 않는다.
 * @param event 기념일 문구
 * @param style 화풍 LoRA 이름
 * @param requestId 같은 값이면 같은 시드로 만든다. 편지 ID 를 넣는다.
 */
public record AiImageRequest(
    String letter, String event, String style, @JsonProperty("request_id") String requestId) {

  public static AiImageRequest of(
      Long letterId, String content, LetterType letterType, LetterThemeType themeType) {
    return new AiImageRequest(
        content, event(letterType), style(themeType), String.valueOf(letterId));
  }

  /** 일반 편지와 유형이 없는 예전 편지는 특정 행사가 없는 기본 프롬프트({@code basic})를 쓴다. */
  static String event(LetterType letterType) {
    if (letterType == null) {
      return "basic";
    }
    return switch (letterType) {
      case BIRTHDAY -> "birthday";
      case CHRISTMAS -> "christmas";
      case COUPLE_ANNIVERSARY -> "anniversary";
      case GENERAL -> "basic";
    };
  }

  /** AI 서버의 {@code cozy} 화풍은 대응하는 편지지 테마가 없어 쓰지 않는다. */
  static String style(LetterThemeType themeType) {
    return switch (themeType) {
      case PASTEL -> "pstl";
      case WATERCOLOR -> "water";
      case VINTAGE -> "vintage";
      case ANIME -> "anime";
      case JOSEON -> "josen";
    };
  }
}
