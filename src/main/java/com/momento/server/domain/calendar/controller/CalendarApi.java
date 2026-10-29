package com.momento.server.domain.calendar.controller;

import com.momento.server.domain.calendar.dto.request.CalendarDailyRequest;
import com.momento.server.domain.calendar.dto.request.CalendarMonthRequest;
import com.momento.server.domain.calendar.dto.response.CalendarDailyResponse;
import com.momento.server.domain.calendar.dto.response.CalendarMonthResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Calendar", description = "캘린더 API")
public interface CalendarApi {

  @Operation(
      summary = "캘린더 월별 조회",
      description =
          "월 달력에 찍을 날짜별 점과 탭 개수를 조회한다. 일정이 있는 날짜만 오름차순으로 주고, 날짜마다 있는 종류를 CAPSULE · "
              + "ANNIVERSARY · MEMORY 순으로 준다. 캡슐은 개봉일, 기념일은 그 해의 기념일 날짜, 아카이브는 추억 날짜에 찍는다. "
              + "매년 반복 기념일은 등록한 해부터 해마다 같은 월 · 일에 찍고(2월 29일은 평년에 2월 28일), 등록한 해보다 앞선 해에는 찍지 않는다. "
              + "캡슐은 내가 참여 중이고 삭제되지 않은 캡슐, 아카이브는 내가 직접 등록하고 삭제되지 않은 추억으로 각 목록과 같다. "
              + "type 을 주면 그 종류만 찍고, 없으면 전체다. counts 는 이 달이 아니라 전체 기간의 종류별 개수로 type 과 상관없이 늘 준다. "
              + "year 가 1900~2100 밖이거나 month 가 1~12 밖이거나 type 이 없는 값이면 400 INVALID_INPUT_VALUE 다.")
  CommonResponse<CalendarMonthResponse> getMonth(
      @Parameter(hidden = true) UserPrincipal principal,
      @ParameterObject @Valid CalendarMonthRequest request);

  @Operation(
      summary = "캘린더 날짜별 조회",
      description =
          "달력에서 날짜를 누르면 그 날의 타임캡슐 · 기념일 · 아카이브를 종류별로 조회한다. capsules 는 그 날 열리는(개봉일이 그 날인) 내 캡슐로 "
              + "타임캡슐 목록의 카드와 같은 형태이고 개봉일 순이다. anniversaries 는 그 날이 기념일인 내 기념일로, 기준은 월별 조회와 같다. "
              + "date 는 조회한 날짜이고 dDay 는 오늘부터 그 날까지 남은 일수다(당일 0, 지나면 음수). memories 는 그 날 추억으로 아카이브 목록의 "
              + "항목과 같은 형태다. type 을 주면 다른 종류는 빈 목록이다. date 가 없거나 형식(yyyy-MM-dd)이 틀리거나 type 이 없는 값이면 "
              + "400 INVALID_INPUT_VALUE 다.")
  CommonResponse<CalendarDailyResponse> getDaily(
      @Parameter(hidden = true) UserPrincipal principal,
      @ParameterObject @Valid CalendarDailyRequest request);
}
