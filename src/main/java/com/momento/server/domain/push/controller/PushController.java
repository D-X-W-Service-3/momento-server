package com.momento.server.domain.push.controller;

import com.momento.server.domain.push.dto.request.PushSubscriptionRequest;
import com.momento.server.domain.push.dto.response.VapidPublicKeyResponse;
import com.momento.server.domain.push.facade.PushFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/push")
@RequiredArgsConstructor
public class PushController implements PushApi {

  private final PushFacade pushFacade;

  @Override
  @GetMapping("/vapid-public-key")
  public CommonResponse<VapidPublicKeyResponse> getVapidPublicKey() {
    return CommonResponse.ok(pushFacade.getVapidPublicKey());
  }

  @Override
  @PostMapping("/subscriptions")
  public CommonResponse<?> subscribe(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody PushSubscriptionRequest request) {
    pushFacade.subscribe(principal.getUserId(), request);
    return CommonResponse.ok();
  }

  @Override
  @DeleteMapping("/subscriptions")
  public CommonResponse<?> unsubscribe(
      @AuthenticationPrincipal UserPrincipal principal, @RequestParam String endpoint) {
    pushFacade.unsubscribe(principal.getUserId(), endpoint);
    return CommonResponse.ok();
  }
}
