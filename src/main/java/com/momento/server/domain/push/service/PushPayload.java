package com.momento.server.domain.push.service;

import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.service.NotificationsSavedEvent;

/**
 * 서비스워커가 받는 푸시 내용. 프론트가 그대로 {@code showNotification} 에 쓰고, 누르면 인앱 알림 목록과 같은 규칙({@code type} +
 * {@code referenceId})으로 이동한다. 서버는 프론트 라우트를 모르므로 이동할 경로를 넣지 않는다.
 *
 * @param body 인앱 알림의 내용. 없을 수 있다.
 */
record PushPayload(
    Long notificationId, NotificationType type, Long referenceId, String title, String body) {

  static PushPayload from(NotificationsSavedEvent.Saved notification) {
    return new PushPayload(
        notification.notificationId(),
        notification.type(),
        notification.referenceId(),
        notification.title(),
        notification.content());
  }
}
