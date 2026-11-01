package com.momento.server.domain.home.controller;

import com.momento.server.domain.home.dto.response.HomeResponse;
import com.momento.server.domain.home.facade.HomeFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@RestApiController("/v1/home")
@RequiredArgsConstructor
public class HomeController implements HomeApi {

  private final HomeFacade homeFacade;

  @Override
  @GetMapping
  public CommonResponse<HomeResponse> getHome(@AuthenticationPrincipal UserPrincipal principal) {
    return CommonResponse.ok(homeFacade.getHome(principal.getUserId()));
  }
}
