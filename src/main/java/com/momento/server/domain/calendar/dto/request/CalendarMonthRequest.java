package com.momento.server.domain.calendar.dto.request;

import com.momento.server.domain.calendar.dto.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 캘린더 월별 조회 요청. 쿼리 파라미터를 {@code @ModelAttribute} 로 받는다. {@code @RequestParam} 에 제약을 걸면 500 이 된다.
 *
 * <p>연도 범위는 아카이브 목록의 연도 필터와 같다.
 */
@Schema(description = "캘린더 월별 조회 요청")
public record CalendarMonthRequest(
    @Schema(description = "연도. 1900~2100.", example = "2026")
        @NotNull(message = "연도를 입력해 주세요.")
        @Min(value = 1900, message = YEAR_RANGE_MESSAGE)
        @Max(value = 2100, message = YEAR_RANGE_MESSAGE)
        Integer year,
    @Schema(description = "월. 1~12.", example = "8")
        @NotNull(message = "월을 입력해 주세요.")
        @Min(value = 1, message = MONTH_RANGE_MESSAGE)
        @Max(value = 12, message = MONTH_RANGE_MESSAGE)
        Integer month,
    @Schema(description = "종류 탭. 없으면 전체.", nullable = true) CalendarType type) {

  static final String YEAR_RANGE_MESSAGE = "연도는 1900 이상 2100 이하여야 합니다.";
  static final String MONTH_RANGE_MESSAGE = "월은 1 이상 12 이하여야 합니다.";
}
