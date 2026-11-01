package com.momento.server.domain.calendar.dto.request;

import com.momento.server.domain.calendar.dto.CalendarSearchSort;
import com.momento.server.domain.calendar.dto.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 통합 검색 요청. 쿼리 파라미터를 {@code @ModelAttribute} 로 받는다. 연도 범위는 월별 조회와 같다.
 *
 * <p>검색어는 캡슐 이름(20자) · 추억 제목(100자)보다 길면 걸리는 것이 없어 100자까지 받는다.
 */
@Schema(description = "통합 검색 요청")
public record CalendarSearchRequest(
    @Schema(description = "검색어. 앞뒤 공백을 빼고 1~100자.", example = "제주")
        @NotBlank(message = "검색어를 입력해 주세요.")
        @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
        String keyword,
    @Schema(description = "종류. 없으면 전체.", nullable = true) CalendarType type,
    @Schema(description = "기간(연도). 1900~2100. 없으면 전체 기간.", example = "2027", nullable = true)
        @Min(value = 1900, message = CalendarMonthRequest.YEAR_RANGE_MESSAGE)
        @Max(value = 2100, message = CalendarMonthRequest.YEAR_RANGE_MESSAGE)
        Integer year,
    @Schema(description = "정렬. 없으면 LATEST.", nullable = true) CalendarSearchSort sort) {

  public CalendarSearchRequest {
    keyword = keyword == null ? null : keyword.strip();
    sort = sort == null ? CalendarSearchSort.LATEST : sort;
  }
}
