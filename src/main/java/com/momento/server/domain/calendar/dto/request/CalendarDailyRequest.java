package com.momento.server.domain.calendar.dto.request;

import com.momento.server.domain.calendar.dto.CalendarType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** 캘린더 날짜별 조회 요청. 쿼리 파라미터를 {@code @ModelAttribute} 로 받는다. */
@Schema(description = "캘린더 날짜별 조회 요청")
public record CalendarDailyRequest(
    @Schema(description = "조회할 날짜", example = "2026-08-12")
        @NotNull(message = "날짜를 입력해 주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,
    @Schema(description = "종류 탭. 없으면 전체.", nullable = true) CalendarType type) {}
