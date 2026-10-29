package com.momento.server.domain.calendar.dto.response;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 고르지 않은 종류는 빈 목록이다. */
@Schema(description = "캘린더 날짜별 조회 응답")
public record CalendarDailyResponse(
    @Schema(description = "조회한 날짜", example = "2026-08-12") LocalDate date,
    @Schema(description = "그 날 열리는 타임캡슐. 타임캡슐 목록의 카드와 같은 형태다.")
        List<TimeCapsuleCardResponse> capsules,
    @Schema(description = "그 날이 기념일인 기념일") List<AnniversaryItem> anniversaries,
    @Schema(description = "그 날 추억. 아카이브 목록의 항목과 같은 형태다.") List<MemorySummary> memories) {

  @Schema(description = "그 날의 기념일")
  public record AnniversaryItem(
      @Schema(description = "기념일 ID", example = "7") Long anniversaryId,
      @Schema(description = "기념일 이름", example = "우리 100일") String title,
      @Schema(description = "이 날짜에 해당하는 기념일 날짜. 조회한 날짜와 같다.", example = "2026-08-12")
          LocalDate date,
      @Schema(description = "오늘부터 date 까지 남은 일수. 당일 0, 지나면 음수다.", example = "0") int dDay) {

    /**
     * @param date 그 기념일의 이 날짜. {@link Anniversary#dateIn} 으로 구한다
     * @param today 서버 시간대의 오늘. 호출하는 쪽이 {@code LocalDate.now(clock)} 으로 넘긴다
     */
    public static AnniversaryItem of(Anniversary anniversary, LocalDate date, LocalDate today) {
      return new AnniversaryItem(
          anniversary.getId(),
          anniversary.getTitle(),
          date,
          (int) ChronoUnit.DAYS.between(today, date));
    }
  }
}
