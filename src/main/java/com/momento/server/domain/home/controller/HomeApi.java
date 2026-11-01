package com.momento.server.domain.home.controller;

import com.momento.server.domain.home.dto.response.HomeResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Home", description = "홈 API")
public interface HomeApi {

  @Operation(
      summary = "홈 조회",
      description =
          "앱을 열면 처음 보이는 홈 화면을 한 번에 조회한다. 조회하면 오늘이 방문으로 기록된다(하루에 여러 번 조회해도 한 번). "
              + "upcomingCapsule 은 내가 참여 중인 작성 중 · 잠긴 캡슐 중 개봉일이 가장 가까운 것이고 없으면 null 이다. "
              + "capsules 는 타임캡슐 탭 홈의 \"곧 만날 타임캡슐\" 과 같은 캡슐을 같은 순서로 최대 3개 주고, 타임캡슐 목록의 카드와 같은 형태다. "
              + "weeklyVisit.days 는 이번 주 월요일부터 일요일까지 7개이고, streak 은 오늘부터 거슬러 올라가 끊기기 전까지 들른 일수로 "
              + "주가 바뀌어도 이어진다. 오늘이 먼저 기록되므로 오늘은 늘 visited 가 true 이고 streak 은 1 이상이다.")
  CommonResponse<HomeResponse> getHome(@Parameter(hidden = true) UserPrincipal principal);
}
