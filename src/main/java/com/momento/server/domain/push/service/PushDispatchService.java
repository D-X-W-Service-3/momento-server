package com.momento.server.domain.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.notification.service.NotificationsSavedEvent;
import com.momento.server.domain.push.entity.PushSubscription;
import com.momento.server.domain.push.external.WebPushClient;
import com.momento.server.domain.push.external.WebPushException;
import com.momento.server.domain.push.repository.PushSubscriptionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장된 인앱 알림을 받은 회원의 모든 구독으로 웹 푸시를 보낸다.
 *
 * <p>알림 트랜잭션이 커밋된 뒤 별도 스레드에서 보낸다. 푸시 서비스는 외부 HTTP 라 느리거나 실패할 수 있는데, 그 때문에 알림 저장이 늦어지거나 롤백되면 안 되고,
 * 롤백된 알림이 푸시로 나가서도 안 되기 때문이다. 발송 실패는 로그만 남기고 다시 보내지 않는다. 인앱 알림 목록에는 이미 남아 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchService {

  /** 구독이 사라졌다는 응답. 브라우저에서 권한을 끊었거나 구독이 만료된 경우다. */
  private static final Set<Integer> GONE = Set.of(404, 410);

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final PushSubscriptionService pushSubscriptionService;
  private final WebPushClient webPushClient;
  private final ObjectMapper objectMapper;

  /** 트랜잭션을 열지 않는다. 열면 푸시 서비스 응답을 기다리는 동안 DB 커넥션을 붙잡는다. */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNotificationsSaved(NotificationsSavedEvent event) {
    Set<Long> userIds =
        event.notifications().stream()
            .map(NotificationsSavedEvent.Saved::userId)
            .collect(Collectors.toSet());
    Map<Long, List<PushSubscription>> subscriptionsByUser =
        pushSubscriptionRepository.findAllByUserIds(userIds).stream()
            .collect(Collectors.groupingBy(subscription -> subscription.getUser().getId()));
    if (subscriptionsByUser.isEmpty()) {
      return;
    }

    Set<String> goneEndpoints = new LinkedHashSet<>();
    for (NotificationsSavedEvent.Saved notification : event.notifications()) {
      List<PushSubscription> subscriptions =
          subscriptionsByUser.getOrDefault(notification.userId(), List.of());
      if (subscriptions.isEmpty()) {
        continue;
      }
      byte[] payload = serialize(PushPayload.from(notification));
      for (PushSubscription subscription : subscriptions) {
        if (goneEndpoints.contains(subscription.getEndpoint())) {
          continue;
        }
        send(notification.notificationId(), subscription, payload, goneEndpoints);
      }
    }

    if (!goneEndpoints.isEmpty()) {
      pushSubscriptionService.removeGone(goneEndpoints);
    }
  }

  private void send(
      Long notificationId,
      PushSubscription subscription,
      byte[] payload,
      Set<String> goneEndpoints) {
    try {
      int status =
          webPushClient.send(
              subscription.getEndpoint(),
              subscription.getP256dh(),
              subscription.getAuth(),
              payload);
      if (GONE.contains(status)) {
        goneEndpoints.add(subscription.getEndpoint());
      } else if (status < 200 || status >= 300) {
        log.warn(
            "웹 푸시 거절: notificationId={}, subscriptionId={}, status={}",
            notificationId,
            subscription.getId(),
            status);
      }
    } catch (WebPushException e) {
      log.warn(
          "웹 푸시 실패: notificationId={}, subscriptionId={}", notificationId, subscription.getId(), e);
    }
  }

  private byte[] serialize(PushPayload payload) {
    try {
      return objectMapper.writeValueAsBytes(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("푸시 페이로드를 만들지 못했습니다.", e);
    }
  }
}
