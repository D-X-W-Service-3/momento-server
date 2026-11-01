package com.momento.server.domain.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 아카이브 수정 요청. 등록과 같은 필드지만 모두 선택이고, 보낸 값만 바꾼다. */
@Schema(description = "아카이브 수정 요청. 보낸 값만 바꾼다.")
public record MemoryUpdateRequest(
    @Schema(description = "아카이브 이름. 최대 15자", example = "첫 도전 성공")
        @Size(max = 15, message = "아카이브 이름은 15자를 넘을 수 없습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "아카이브 이름은 비어 있을 수 없습니다.")
        String title,
    @Schema(description = "추억 내용. 최대 45자", example = "오늘 친구들과 카페에서 오랜만에 만났다")
        @Size(max = 45, message = "추억 내용은 45자를 넘을 수 없습니다.")
        String content,
    @Schema(description = "추억 날짜", example = "2027-03-01") LocalDate memoryDate,
    @Schema(
            description =
                "수정 후 최종 사진 key 목록. 보여줄 순서대로, 최대 8개. 보내면 사진 목록 전체를 이 값으로 바꾸고, 빠진 사진은 삭제된다. 기존 사진은"
                    + " 상세 조회의 imageKey 를, 새 사진은 이미지 업로드 URL 발급으로 받은 key 를 넣는다. 보내지 않으면 사진은 그대로다.")
        @Size(max = 8, message = "사진은 최대 8장까지 등록할 수 있습니다.")
        List<
                @NotBlank(message = "이미지 key 는 비어 있을 수 없습니다.")
                @Size(max = 500, message = "이미지 key 는 500자를 넘을 수 없습니다.") String>
            imageKeys,
    @Schema(description = "대표 사진 key. 최종 사진 목록 중 하나다. 보내면 그 사진을 첫 번째로 옮긴다.")
        String representativeImageKey) {

  /** 사진 목록이나 대표 사진을 바꾸는 요청인지. */
  public boolean changesImages() {
    return imageKeys != null || representativeImageKey != null;
  }
}
