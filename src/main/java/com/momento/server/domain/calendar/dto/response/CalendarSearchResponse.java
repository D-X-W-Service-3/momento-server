package com.momento.server.domain.calendar.dto.response;

import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 고르지 않은 종류는 빈 목록이다. 세 목록 모두 요청한 정렬을 따른다. */
@Schema(description = "통합 검색 응답")
public record CalendarSearchResponse(
    @Schema(description = "전체 결과 수. 세 목록 길이의 합이다.", example = "3") int totalCount,
    @Schema(description = "타임캡슐. 타임캡슐 목록의 카드와 같은 형태다.") List<TimeCapsuleCardResponse> capsules,
    @Schema(description = "기념일. 날짜별 조회의 기념일과 같은 형태다. 매년 반복이면 기간이 없을 때 다가오는 날, 기간이 있으면 그 해의 날짜다.")
        List<CalendarDailyResponse.AnniversaryItem> anniversaries,
    @Schema(description = "아카이브. 아카이브 목록의 항목과 같은 형태다.") List<MemorySummary> memories) {

  public static CalendarSearchResponse of(
      List<TimeCapsuleCardResponse> capsules,
      List<CalendarDailyResponse.AnniversaryItem> anniversaries,
      List<MemorySummary> memories) {
    return new CalendarSearchResponse(
        capsules.size() + anniversaries.size() + memories.size(),
        capsules,
        anniversaries,
        memories);
  }
}
