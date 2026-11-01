package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "타임캡슐 목록 조회 응답")
public record TimeCapsuleListResponse(
    @Schema(description = "탭별 개수. 검색어는 적용하고 상태 탭은 적용하지 않는다.") Counts counts,
    @Schema(description = "캡슐 목록(개봉일 오름차순)") List<TimeCapsuleCardResponse> capsules,
    @Schema(
            description = "다음 페이지 조회에 쓸 커서. 다음 페이지가 없으면 null",
            example = "MjA0NS0wMS0wMVQwMDowMHwyNQ",
            nullable = true)
        String nextCursor,
    @Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

  @Schema(description = "탭별 개수")
  public record Counts(
      @Schema(description = "전체. 세 탭의 합이다.", example = "32") long all,
      @Schema(description = "개봉 가능", example = "8") long opened,
      @Schema(description = "수정 가능", example = "20") long writing,
      @Schema(description = "잠긴 캡슐", example = "4") long locked) {

    /** 캡슐이 없는 상태는 0 으로 채운다. */
    public static Counts of(Map<CapsuleStatus, Long> byStatus) {
      long opened = byStatus.getOrDefault(CapsuleStatus.OPENED, 0L);
      long writing = byStatus.getOrDefault(CapsuleStatus.WRITING, 0L);
      long locked = byStatus.getOrDefault(CapsuleStatus.LOCKED, 0L);
      return new Counts(opened + writing + locked, opened, writing, locked);
    }
  }
}
