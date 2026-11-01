package com.momento.server.domain.notification.dto.response;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "알림 목록 조회 응답")
public record NotificationListResponse(
    @Schema(description = "안 읽은 알림 개수", example = "3") long unreadCount,
    @Schema(description = "알림 목록(최신순)") List<Item> notifications,
    @Schema(description = "다음 페이지 조회에 쓸 커서. 다음 페이지가 없으면 null", example = "88", nullable = true)
        String nextCursor,
    @Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

  public static NotificationListResponse of(
      long unreadCount, List<Notification> notifications, String nextCursor, boolean hasNext) {
    List<Item> items = notifications.stream().map(Item::from).toList();
    return new NotificationListResponse(unreadCount, items, nextCursor, hasNext);
  }

  @Schema(description = "알림 항목")
  public record Item(
      @Schema(description = "알림 ID", example = "101") Long notificationId,
      @Schema(description = "알림 유형") NotificationType type,
      @Schema(description = "알림 제목") String title,
      @Schema(description = "알림 내용") String content,
      @Schema(description = "읽음 여부") boolean isRead,
      @Schema(description = "생성 일시") LocalDateTime createdAt,
      @Schema(description = "알림이 가리키는 리소스 ID", example = "12", nullable = true) Long referenceId) {

    public static Item from(Notification notification) {
      return new Item(
          notification.getId(),
          notification.getNotificationType(),
          notification.getTitle(),
          notification.getContent(),
          notification.isRead(),
          notification.getCreatedAt(),
          notification.getReferenceId());
    }
  }
}
