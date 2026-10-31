package com.momento.server.domain.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "추억 등록 요청")
public record MemoryCreateRequest(
    @Schema(
            description = "아카이브 이름. 최대 15자",
            example = "첫 도전 성공",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "아카이브 이름은 필수입니다.")
        @Size(max = 15, message = "아카이브 이름은 15자를 넘을 수 없습니다.")
        String title,
    @Schema(description = "추억 내용. 최대 45자", example = "오늘 친구들과 카페에서 오랜만에 만났다")
        @Size(max = 45, message = "추억 내용은 45자를 넘을 수 없습니다.")
        String content,
    @Schema(
            description = "추억이 있었던 날짜. 캘린더 노출 기준이라 필수다.",
            example = "2026-08-15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "추억 날짜는 필수입니다.")
        LocalDate memoryDate,
    @Schema(
            description =
                "사진 S3 key. 보여줄 순서대로, 최대 8개. 이미지 업로드 URL 발급(target=MEMORY)으로 받은 내 key 여야 한다."
                    + " 없으면 생략하거나 빈 배열로 보낸다.",
            example = "[\"memories/1/3f0a5c9e-2b1d-4f7a-8c3e-9d2b7a6e1f00.jpg\"]")
        @Size(max = 8, message = "사진은 최대 8장까지 등록할 수 있습니다.")
        List<
                @NotBlank(message = "이미지 key 는 비어 있을 수 없습니다.")
                @Size(max = 500, message = "이미지 key 는 500자를 넘을 수 없습니다.") String>
            imageKeys,
    @Schema(
            description = "대표 사진 key. imageKeys 중 하나다. 없으면 첫 번째 사진이 대표 사진이다.",
            example = "memories/1/3f0a5c9e-2b1d-4f7a-8c3e-9d2b7a6e1f00.jpg")
        String representativeImageKey) {

  /** imageKeys 를 아예 안 보낸 경우 null 이 되므로, 서비스가 쓰기 편하게 빈 목록으로 바꿔 준다. */
  public List<String> imageKeysOrEmpty() {
    return imageKeys == null ? List.of() : imageKeys;
  }
}
