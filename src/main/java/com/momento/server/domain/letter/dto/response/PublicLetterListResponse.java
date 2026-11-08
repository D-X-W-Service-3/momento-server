package com.momento.server.domain.letter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.momento.server.domain.image.entity.GeneratedImage;
import com.momento.server.domain.image.entity.GenerationStatus;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Schema(description = "공개된 편지 목록")
public record PublicLetterListResponse(List<Item> letters) {
  /**
   * @param imagesByLetterId 편지 ID 별 AI 이미지. 이미지가 없는 편지는 빠져 있다.
   * @param viewUrlOf 저장된 key 를 조회 주소로 바꾼다
   */
  public static PublicLetterListResponse from(
      List<Letter> letters,
      Map<Long, GeneratedImage> imagesByLetterId,
      Function<String, String> viewUrlOf) {
    return new PublicLetterListResponse(
        letters.stream()
            .map(letter -> Item.from(letter, imagesByLetterId.get(letter.getId()), viewUrlOf))
            .toList());
  }

  @Schema(name = "PublicLetterListItem", description = "공개된 편지 하나")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Item(
      Long letterId,
      @Schema(description = "편지 유형. 기존 데이터는 null일 수 있다.", nullable = true) LetterType letterType,
      @Schema(description = "편지지 화풍. 기존 데이터는 null일 수 있다.", nullable = true)
          LetterThemeType themeType,
      @Schema(description = "편지 제목. 기존 데이터는 null일 수 있다.", nullable = true) String title,
      String content,
      String authorNickname,
      LocalDateTime createdAt,
      @Schema(
              description = "AI 이미지 조회용 presigned URL(1시간 유효). imageStatus 가 COMPLETED 일 때만 있다.",
              nullable = true)
          String imageUrl,
      @Schema(
              description = "AI 이미지 생성 상태. PENDING 이면 생성 중이고, 이미지 생성 대상이 아닌 예전 편지는 null 이다.",
              nullable = true)
          GenerationStatus imageStatus) {
    private static Item from(
        Letter letter, GeneratedImage image, Function<String, String> viewUrlOf) {
      return new Item(
          letter.getId(),
          letter.getLetterType(),
          letter.getThemeType(),
          letter.getTitle(),
          letter.getContent(),
          letter.getAuthor().getNickname(),
          letter.getCreatedAt(),
          LetterImage.viewUrl(image, viewUrlOf),
          LetterImage.status(image));
    }
  }
}
