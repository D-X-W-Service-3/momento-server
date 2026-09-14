package com.momento.server.domain.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "추억 등록 요청")
public record MemoryCreateRequest(
    @Schema(description = "추억 제목", example = "제주도 여행", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
        String title,
    @Schema(description = "추억 내용", example = "바다가 정말 예뻤다") String content,
    @Schema(
            description = "추억이 있었던 날짜. 캘린더 노출 기준이라 필수다.",
            example = "2026-08-15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "추억 날짜는 필수입니다.")
        LocalDate memoryDate,
    @Schema(description = "이미 업로드해 확보한 이미지 URL 목록. 최대 10장이며, 없으면 생략하거나 빈 배열로 보낸다.")
        @Size(max = 10, message = "이미지는 최대 10장까지 등록할 수 있습니다.")
        List<String> imageUrls) {

  /** imageUrls 를 아예 안 보낸 경우 null 이 되므로, 서비스가 쓰기 편하게 빈 목록으로 바꿔 준다. */
  public List<String> imageUrlsOrEmpty() {
    return imageUrls == null ? List.of() : imageUrls;
  }
}
