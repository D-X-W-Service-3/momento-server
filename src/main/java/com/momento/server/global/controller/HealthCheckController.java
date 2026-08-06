package com.momento.server.global.controller;

import static com.momento.server.global.common.code.SuccessCode.OK;

import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.dto.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;

@RestApiController("/health-check")
public class HealthCheckController {

  @GetMapping
  public CommonResponse<String> getHealthCheck() {
    return CommonResponse.success(OK);
  }
}
