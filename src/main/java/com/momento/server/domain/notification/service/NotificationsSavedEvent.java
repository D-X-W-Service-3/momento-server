package com.momento.server.domain.notification.service;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import java.util.List;

/**
 * 인앱 알림을 저장했을 때 발행한다. 웹 푸시 발송이 여기에 붙는다.
 *
 * <p>받을 회원은 저장 전에 알림 설정 · 탈퇴 여부로 이미 걸렀으므로, 여기 담긴 알림은 그대로 보내면 된다. 받는 쪽은 {@code AFTER_COMMIT} 으로 받아
 * 롤백된 알림을 보내지 않게 한다. 엔티티가 아니라 값만 담아 트랜잭션이 끝난 뒤에도 지연 로딩 없이 읽게 한다.
 */
public record NotificationsSavedEvent(List<Saved> notifications) {

  public static NotificationsSavedEvent of(List<Notification> notifications) {
    return new NotificationsSavedEvent(notifications.stream().map(Saved::from).toList());
  }

  public record Saved(
      Long notificationId,
      Long userId,
      NotificationType type,
      Long referenceId,
      String title,
      String content) {

    static Saved from(Notification notification) {
      return new Saved(
          notification.getId(),
          notification.getUser().getId(),
          notification.getNotificationType(),
          notification.getReferenceId(),
          notification.getTitle(),
          notification.getContent());
    }
  }
}
