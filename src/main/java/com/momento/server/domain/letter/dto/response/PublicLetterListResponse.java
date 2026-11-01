package com.momento.server.domain.letter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.momento.server.domain.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "공개된 편지 목록")
public record PublicLetterListResponse(List<Item> letters) {
  public static PublicLetterListResponse from(List<Letter> letters) {
    return new PublicLetterListResponse(letters.stream().map(Item::from).toList());
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Item(
      Long letterId,
      @Schema(description = "편지지 화풍. 기존 데이터는 null일 수 있다.", nullable = true) String themeType,
      @Schema(description = "편지 제목. 기존 데이터는 null일 수 있다.", nullable = true) String title,
      String content,
      String authorNickname,
      LocalDateTime createdAt,
      @Schema(description = "AI 연계 보류로 현재 null을 반환한다.", nullable = true) String imageUrl) {
    private static Item from(Letter letter) {
      return new Item(
          letter.getId(),
          letter.getThemeType(),
          letter.getTitle(),
          letter.getContent(),
          letter.getAuthor().getNickname(),
          letter.getCreatedAt(),
          null);
    }
  }
}
