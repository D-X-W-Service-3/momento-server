package com.momento.server.domain.calendar.dto.response;

import com.momento.server.domain.calendar.dto.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

@Schema(description = "캘린더 월별 조회 응답")
public record CalendarMonthResponse(
    @Schema(description = "연도", example = "2026") int year,
    @Schema(description = "월", example = "8") int month,
    @Schema(description = "종류별 개수. 이 달이 아니라 전체 기간이고, type 과 상관없이 늘 센다.") Counts counts,
    @Schema(description = "일정이 있는 날짜만, 날짜 오름차순") List<Day> days) {

  /**
   * @param types 날짜 순으로 정렬된 날짜별 종류. 종류는 {@link CalendarType} 순서로 순회해야 한다({@code EnumSet})
   */
  public static CalendarMonthResponse of(
      int year, int month, Counts counts, SortedMap<LocalDate, ? extends Set<CalendarType>> types) {
    List<Day> days =
        types.entrySet().stream()
            .map(entry -> new Day(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
    return new CalendarMonthResponse(year, month, counts, days);
  }

  @Schema(description = "종류별 개수")
  public record Counts(
      @Schema(description = "전체. 세 종류의 합이다.", example = "32") long all,
      @Schema(description = "캡슐. 타임캡슐 목록의 전체 개수와 같다.", example = "8") long capsule,
      @Schema(description = "아카이브. 아카이브 목록의 개수와 같다.", example = "12") long memory,
      @Schema(description = "기념일. 매년 반복 기념일도 한 개로 센다.", example = "12") long anniversary) {

    public static Counts of(long capsule, long memory, long anniversary) {
      return new Counts(capsule + memory + anniversary, capsule, memory, anniversary);
    }
  }

  @Schema(description = "일정이 있는 날짜")
  public record Day(
      @Schema(description = "날짜", example = "2026-08-12") LocalDate date,
      @Schema(description = "그 날 있는 종류. CAPSULE · ANNIVERSARY · MEMORY 순이다.")
          List<CalendarType> types) {}
}
