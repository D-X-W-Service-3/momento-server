package com.momento.server.domain.notification.facade;

import com.momento.server.domain.notification.dto.response.NotificationListResponse;
import com.momento.server.domain.notification.service.NotificationService;
import com.momento.server.domain.notification.service.NotificationService.NotificationPage;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationFacade {

  private final NotificationService notificationService;

  public NotificationListResponse getNotifications(Long userId, String cursor) {
    Long parsedCursor = parseCursor(cursor);
    NotificationPage page = notificationService.getNotifications(userId, parsedCursor);
    long unreadCount = notificationService.countUnread(userId);

    String nextCursor = page.nextCursor() == null ? null : String.valueOf(page.nextCursor());
    return NotificationListResponse.of(
        unreadCount, page.notifications(), nextCursor, page.hasNext());
  }

  public void markAsRead(Long userId, Long notificationId) {
    notificationService.markAsRead(userId, notificationId);
  }

  private Long parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(cursor);
    } catch (NumberFormatException exception) {
      throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE, exception);
    }
  }
}
