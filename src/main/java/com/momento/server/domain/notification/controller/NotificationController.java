package com.momento.server.domain.notification.controller;

import com.momento.server.domain.notification.dto.response.NotificationListResponse;
import com.momento.server.domain.notification.facade.NotificationFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

  private final NotificationFacade notificationFacade;

  @Override
  @GetMapping
  public CommonResponse<NotificationListResponse> getNotifications(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String cursor) {
    return CommonResponse.ok(notificationFacade.getNotifications(principal.getUserId(), cursor));
  }

  @Override
  @PatchMapping("/{notificationId}/read")
  public CommonResponse<?> readNotification(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long notificationId) {
    notificationFacade.markAsRead(principal.getUserId(), notificationId);
    return CommonResponse.ok();
  }
}
