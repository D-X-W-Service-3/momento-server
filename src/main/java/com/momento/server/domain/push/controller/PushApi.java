package com.momento.server.domain.push.controller;

import com.momento.server.domain.push.dto.request.PushSubscriptionRequest;
import com.momento.server.domain.push.dto.response.VapidPublicKeyResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Push", description = "웹 푸시 구독 API")
public interface PushApi {

  @Operation(
      summary = "VAPID 공개키 조회",
      description = "pushManager.subscribe 의 applicationServerKey 로 쓸 공개키를 조회한다. 로그인하지 않아도 된다.")
  CommonResponse<VapidPublicKeyResponse> getVapidPublicKey();

  @Operation(
      summary = "웹 푸시 구독 등록",
      description =
          "브라우저의 PushSubscription.toJSON() 결과를 그대로 보낸다. 같은 endpoint 로 다시 보내면 키를 갱신하고, 다른 회원으로"
              + " 보내면 소유자를 그 회원으로 바꾼다. 한 회원이 기기마다 여러 구독을 가질 수 있다.")
  CommonResponse<?> subscribe(
      @Parameter(hidden = true) UserPrincipal principal, PushSubscriptionRequest request);

  @Operation(
      summary = "웹 푸시 구독 해제",
      description = "로그아웃 · 알림 끄기 때 호출한다. 로그아웃이면 토큰을 지우기 전에 호출해야 한다. 없거나 남의 구독이어도 성공 응답한다.")
  CommonResponse<?> unsubscribe(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "해제할 구독의 endpoint. URL 인코딩해서 보낸다.") String endpoint);
}
