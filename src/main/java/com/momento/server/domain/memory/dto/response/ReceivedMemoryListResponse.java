package com.momento.server.domain.memory.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Schema(description = "받은 아카이브 조회 결과")
public record ReceivedMemoryListResponse(
    @Schema(description = "받은 아카이브 목록. 받은 순") List<Item> memories) {

  @Schema(description = "받은 아카이브 하나")
  public record Item(
      @Schema(description = "받은 아카이브 식별자. 저장 · 닫기에 쓴다.", example = "4") Long receivedMemoryId,
      @Schema(description = "공유한 사람 닉네임", example = "유빈") String senderNickname,
      @Schema(description = "아카이브 이름", example = "2024 우리, 다시 만나는 날") String title,
      @Schema(description = "추억 내용", nullable = true) String content,
      @Schema(description = "추억 날짜", example = "2027-03-01") LocalDate memoryDate,
      @Schema(description = "사진 목록. displayOrder 오름차순") List<Image> images) {}

  /** 아카이브 상세의 사진에서 imageKey 를 뺀 형태다. 받은 사람은 수정할 수 없어 key 가 필요 없다. */
  @Schema(description = "받은 아카이브 사진 하나")
  public record Image(
      @Schema(description = "사진 식별자", example = "11") Long imageId,
      @Schema(description = "사진 URL(presigned GET, 1시간)") String imageUrl,
      @Schema(description = "보여줄 순서. 0부터", example = "0") int displayOrder,
      @Schema(description = "대표 사진 여부. 첫 번째 사진이 대표 사진이다.", example = "true")
          @JsonProperty("isRepresentative")
          boolean isRepresentative) {}

  /**
   * @param imagesByMemory 추억 ID 별 사진. display_order 오름차순이어야 한다
   * @param viewUrlOf 저장된 key 를 조회 주소로 바꾼다
   */
  public static ReceivedMemoryListResponse of(
      List<ReceivedMemory> received,
      Map<Long, List<MemoryImage>> imagesByMemory,
      Function<String, String> viewUrlOf) {
    return new ReceivedMemoryListResponse(
        received.stream()
            .map(
                item -> {
                  Memory memory = item.getMemory();
                  return new Item(
                      item.getId(),
                      memory.getUser().getNickname(),
                      memory.getTitle(),
                      memory.getContent(),
                      memory.getMemoryDate(),
                      images(imagesByMemory.getOrDefault(memory.getId(), List.of()), viewUrlOf));
                })
            .toList());
  }

  private static List<Image> images(List<MemoryImage> images, Function<String, String> viewUrlOf) {
    List<Image> responses = new ArrayList<>();
    for (int i = 0; i < images.size(); i++) {
      MemoryImage image = images.get(i);
      responses.add(
          new Image(
              image.getId(),
              viewUrlOf.apply(image.getImageUrl()),
              image.getDisplayOrder(),
              i == 0));
    }
    return responses;
  }
}
