package com.momento.server.domain.notification.entity;

import com.momento.server.domain.user.entity.User;
import java.time.LocalDate;

/**
 * 보낼 알림 한 건. 알림마다 제목 · 내용 · 이동 대상 · 중복 방지 키를 정하는 규칙을 한곳에 둔다.
 *
 * <p>디자인에 문구가 있는 알림(공개 · D-Day)은 그 문구를 그대로 쓴다. 나머지는 이름(캡슐 · 기념일 · 추억)을 조사 없이 붙여, 받침에 따라 문장이 어색해지지
 * 않게 한다.
 *
 * @param dedupKey 회원마다 유일한 중복 방지 키. 날짜가 기준인 알림은 보낸 날짜를 넣어 하루에 한 번만 가게 한다. 비어 있으면 중복을 막지 않는다
 */
public record NotificationDraft(
    Long userId,
    NotificationType type,
    Long referenceId,
    String title,
    String content,
    String dedupKey) {

  /** 공개는 캡슐마다 한 번뿐이라 날짜를 넣지 않는다. */
  public static NotificationDraft capsuleOpened(Long userId, Long capsuleId) {
    return new NotificationDraft(
        userId,
        NotificationType.CAPSULE_OPENED,
        capsuleId,
        "타임캡슐이 공개되었어요",
        "지금바로 확인해보세요",
        key(NotificationType.CAPSULE_OPENED, capsuleId));
  }

  public static NotificationDraft capsuleDDay(
      Long userId, Long capsuleId, long daysLeft, LocalDate today) {
    return new NotificationDraft(
        userId,
        NotificationType.CAPSULE_D_DAY,
        capsuleId,
        "캡슐 개봉 " + daysLeft + "일 전이에요",
        "어떤 캡슐인지 확인해보세요",
        key(NotificationType.CAPSULE_D_DAY, capsuleId, today));
  }

  /** 마감은 캡슐마다 한 번뿐이라 날짜를 넣지 않는다. 마감일 하루가 끝나는 23:59:59 에 잠긴다. */
  public static NotificationDraft letterDeadline(Long userId, Long capsuleId, String capsuleTitle) {
    return new NotificationDraft(
        userId,
        NotificationType.LETTER_DEADLINE,
        capsuleId,
        "오늘 편지 수정이 마감돼요",
        "'" + capsuleTitle + "' 캡슐의 편지는 오늘까지 고칠 수 있어요",
        key(NotificationType.LETTER_DEADLINE, capsuleId));
  }

  /** 나갔던 사람이 다시 들어와도 새 참여로 알리므로 중복 방지 키를 두지 않는다. */
  public static NotificationDraft newMember(
      Long userId, Long capsuleId, String capsuleTitle, String joinerNickname) {
    return new NotificationDraft(
        userId,
        NotificationType.NEW_MEMBER,
        capsuleId,
        "새로운 참여자가 들어왔어요",
        joinerNickname + "님이 '" + capsuleTitle + "' 캡슐에 참여했어요",
        null);
  }

  /** 하루에 한 건만 보낸다(같은 날 추억이 여러 개여도). 키에 추억 ID 를 넣지 않는 이유다. */
  public static NotificationDraft memoryReminder(
      Long userId, Long memoryId, String memoryTitle, int yearsAgo, LocalDate today) {
    return new NotificationDraft(
        userId,
        NotificationType.MEMORY_REMINDER,
        memoryId,
        yearsAgo + "년 전 오늘의 추억이 있어요",
        memoryTitle,
        key(NotificationType.MEMORY_REMINDER, today));
  }

  /**
   * @param daysLeft 기념일까지 남은 일수. 0 이면 당일 알림이다
   */
  public static NotificationDraft anniversary(
      Long userId, Long anniversaryId, String title, long daysLeft, LocalDate today) {
    return new NotificationDraft(
        userId,
        NotificationType.ANNIVERSARY_REMINDER,
        anniversaryId,
        daysLeft == 0 ? "오늘은 기념일이에요" : "기념일이 " + daysLeft + "일 남았어요",
        title,
        key(NotificationType.ANNIVERSARY_REMINDER, anniversaryId, today));
  }

  public Notification toEntity(User user) {
    return Notification.builder()
        .user(user)
        .notificationType(type)
        .referenceId(referenceId)
        .title(title)
        .content(content)
        .dedupKey(dedupKey)
        .build();
  }

  private static String key(NotificationType type, Object... parts) {
    StringBuilder key = new StringBuilder(type.name());
    for (Object part : parts) {
      key.append(':').append(part);
    }
    return key.toString();
  }
}
