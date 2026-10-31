package com.momento.server.domain.notification.service;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationDraft;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.notification.repository.SentKey;
import com.momento.server.domain.user.repository.UserRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림을 저장한다. 받을 회원을 고르는 일(설정 · 탈퇴 확인)은 부르는 쪽의 조회가 이미 했다. */
@Service
@RequiredArgsConstructor
public class NotificationSender {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  /**
   * 중복 방지 키가 있는 알림 중 이미 보낸 것은 건너뛰고 나머지를 저장한다. 같은 묶음 안에 같은 (회원, 키)가 둘이면 하나만 남긴다.
   *
   * <p>(user_id, dedup_key) UNIQUE 가 마지막 방어선이다. 서버가 한 대인 동안에는 미리 걸러 위반이 나지 않는다. 여러 대가 같은 묶음을 동시에
   * 보내면 늦은 쪽 트랜잭션이 통째로 실패하고, 다음 주기에 이미 보낸 것을 건너뛰고 나머지를 보낸다.
   *
   * @return 저장한 알림 수
   */
  @Transactional
  public int send(Collection<NotificationDraft> drafts) {
    if (drafts.isEmpty()) {
      return 0;
    }
    Set<SentKey> sent = findSent(drafts);

    Map<Object, NotificationDraft> unsent = new LinkedHashMap<>();
    for (NotificationDraft draft : drafts) {
      if (draft.dedupKey() == null) {
        unsent.put(new Object(), draft);
        continue;
      }
      SentKey key = new SentKey(draft.userId(), draft.dedupKey());
      if (!sent.contains(key)) {
        unsent.putIfAbsent(key, draft);
      }
    }

    List<Notification> notifications =
        unsent.values().stream()
            .map(draft -> draft.toEntity(userRepository.getReferenceById(draft.userId())))
            .toList();
    notificationRepository.saveAll(notifications);
    return notifications.size();
  }

  private Set<SentKey> findSent(Collection<NotificationDraft> drafts) {
    List<NotificationDraft> keyed =
        drafts.stream().filter(draft -> draft.dedupKey() != null).toList();
    if (keyed.isEmpty()) {
      return Set.of();
    }
    Set<Long> userIds = new HashSet<>();
    Set<String> keys = new HashSet<>();
    for (NotificationDraft draft : keyed) {
      userIds.add(draft.userId());
      keys.add(Objects.requireNonNull(draft.dedupKey()));
    }
    return new HashSet<>(notificationRepository.findSentKeys(userIds, keys));
  }
}
