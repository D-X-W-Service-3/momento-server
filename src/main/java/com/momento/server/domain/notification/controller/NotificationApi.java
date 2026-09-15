package com.momento.server.domain.notification.controller;

import com.momento.server.domain.notification.dto.response.NotificationListResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification", description = "알림 API")
public interface NotificationApi {

  @Operation(
      summary = "알림 목록 조회",
      description = "로그인한 회원의 알림을 최신순으로 조회한다. cursor 를 생략하면 첫 페이지를 반환한다.")
  CommonResponse<NotificationListResponse> getNotifications(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "이전 응답의 nextCursor 값. 생략하면 첫 페이지") String cursor);

  @Operation(summary = "알림 읽음 처리", description = "알림 하나를 읽음 처리한다. 이미 읽은 알림이면 상태 변화 없이 성공 응답한다.")
  CommonResponse<?> readNotification(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "읽음 처리할 알림 ID") Long notificationId);
}
