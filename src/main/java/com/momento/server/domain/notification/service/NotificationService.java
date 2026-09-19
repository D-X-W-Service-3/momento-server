package com.momento.server.domain.notification.service;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.exception.NotificationErrorCode;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  /** 명세에 페이지 크기 파라미터가 없어 고정값으로 둔다. */
  private static final int PAGE_SIZE = 20;

  private final NotificationRepository notificationRepository;
  private final Clock clock;

  public NotificationPage getNotifications(Long userId, Long cursor) {
    List<Notification> fetched =
        notificationRepository.findPage(userId, cursor, PageRequest.of(0, PAGE_SIZE + 1));

    boolean hasNext = fetched.size() > PAGE_SIZE;
    List<Notification> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;
    Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

    return new NotificationPage(page, nextCursor, hasNext);
  }

  public long countUnread(Long userId) {
    return notificationRepository.countByUserIdAndReadFalse(userId);
  }

  /** 타 회원 알림·존재하지 않는 알림은 구분 없이 404 다. 멱등 처리(이미 읽었으면 유지)는 {@link Notification#markAsRead} 책임이다. */
  @Transactional
  public void markAsRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

    notification.markAsRead(LocalDateTime.now(clock));
  }

  public record NotificationPage(
      List<Notification> notifications, Long nextCursor, boolean hasNext) {}
}
