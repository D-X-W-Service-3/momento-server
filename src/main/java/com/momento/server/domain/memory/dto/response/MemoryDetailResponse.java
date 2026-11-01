package com.momento.server.domain.memory.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Schema(description = "아카이브 상세")
public record MemoryDetailResponse(
    @Schema(description = "아카이브 식별자", example = "3") Long memoryId,
    @Schema(description = "아카이브 이름", example = "첫 도전 성공") String title,
    @Schema(description = "추억 내용", nullable = true) String content,
    @Schema(description = "추억 날짜", example = "2027-03-01") LocalDate memoryDate,
    @Schema(description = "작성자 닉네임", example = "유빈") String authorNickname,
    @Schema(description = "내가 작성했는지 여부. 수정 · 삭제 메뉴 노출에 쓴다.", example = "true")
        @JsonProperty("isMine")
        boolean isMine,
    @Schema(description = "사진 목록. displayOrder 오름차순") List<Image> images,
    @Schema(description = "등록 일시") LocalDateTime createdAt) {

  @Schema(description = "아카이브 사진 하나")
  public record Image(
      @Schema(description = "사진 식별자", example = "11") Long imageId,
      @Schema(
              description = "S3 key. 수정 요청의 imageKeys 에 그대로 보낸다.",
              example = "memories/1/3f0a5c9e-2b1d-4f7a-8c3e-9d2b7a6e1f00.jpg")
          String imageKey,
      @Schema(description = "사진 URL(presigned GET, 1시간)") String imageUrl,
      @Schema(description = "보여줄 순서. 0부터", example = "0") int displayOrder,
      @Schema(description = "대표 사진 여부. 첫 번째 사진이 대표 사진이다.", example = "true")
          @JsonProperty("isRepresentative")
          boolean isRepresentative) {}

  /** images 는 display_order 오름차순이어야 한다. viewUrlOf 는 저장된 key 를 조회 주소로 바꾼다. */
  public static MemoryDetailResponse of(
      Memory memory,
      List<MemoryImage> images,
      String authorNickname,
      boolean isMine,
      Function<String, String> viewUrlOf) {
    List<Image> imageResponses = new ArrayList<>();
    for (int i = 0; i < images.size(); i++) {
      MemoryImage image = images.get(i);
      imageResponses.add(
          new Image(
              image.getId(),
              image.getImageUrl(),
              viewUrlOf.apply(image.getImageUrl()),
              image.getDisplayOrder(),
              i == 0));
    }

    return new MemoryDetailResponse(
        memory.getId(),
        memory.getTitle(),
        memory.getContent(),
        memory.getMemoryDate(),
        authorNickname,
        isMine,
        imageResponses,
        memory.getCreatedAt());
  }
}
