package com.momento.server.domain.calendar.controller;

import com.momento.server.domain.calendar.dto.request.CalendarDailyRequest;
import com.momento.server.domain.calendar.dto.request.CalendarMonthRequest;
import com.momento.server.domain.calendar.dto.request.CalendarSearchRequest;
import com.momento.server.domain.calendar.dto.response.CalendarDailyResponse;
import com.momento.server.domain.calendar.dto.response.CalendarMonthResponse;
import com.momento.server.domain.calendar.dto.response.CalendarSearchResponse;
import com.momento.server.domain.calendar.facade.CalendarFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@RestApiController("/v1/calendar")
@RequiredArgsConstructor
public class CalendarController implements CalendarApi {

  private final CalendarFacade calendarFacade;

  @Override
  @GetMapping
  public CommonResponse<CalendarMonthResponse> getMonth(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute CalendarMonthRequest request) {
    return CommonResponse.ok(calendarFacade.getMonth(principal.getUserId(), request));
  }

  @Override
  @GetMapping("/daily")
  public CommonResponse<CalendarDailyResponse> getDaily(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute CalendarDailyRequest request) {
    return CommonResponse.ok(calendarFacade.getDaily(principal.getUserId(), request));
  }

  @Override
  @GetMapping("/search")
  public CommonResponse<CalendarSearchResponse> search(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute CalendarSearchRequest request) {
    return CommonResponse.ok(calendarFacade.search(principal.getUserId(), request));
  }
}
