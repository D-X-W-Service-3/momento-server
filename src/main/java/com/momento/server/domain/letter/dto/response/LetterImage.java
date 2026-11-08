package com.momento.server.domain.letter.dto.response;

import com.momento.server.domain.image.entity.GeneratedImage;
import com.momento.server.domain.image.entity.GenerationStatus;
import java.util.function.Function;

/** 편지 응답에 붙이는 AI 이미지 필드. 내 편지와 공개 편지 목록이 같은 규칙을 쓴다. */
final class LetterImage {

  private LetterImage() {}

  /** 생성이 끝난 이미지만 조회 주소를 만든다. */
  static String viewUrl(GeneratedImage image, Function<String, String> viewUrlOf) {
    if (image == null || image.getGenerationStatus() != GenerationStatus.COMPLETED) {
      return null;
    }
    return viewUrlOf.apply(image.getImageUrl());
  }

  static GenerationStatus status(GeneratedImage image) {
    return image == null ? null : image.getGenerationStatus();
  }
}
