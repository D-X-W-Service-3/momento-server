package com.momento.server.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationDraft;
import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.notification.service.NotificationSender;
import com.momento.server.domain.push.entity.PushSubscription;
import com.momento.server.domain.push.external.WebPushClient;
import com.momento.server.domain.push.external.WebPushException;
import com.momento.server.domain.push.repository.PushSubscriptionRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 인앱 알림이 저장되면 커밋 뒤 받은 회원의 구독으로 웹 푸시가 나가는지 검증한다. 실제 푸시 서비스 대신 가짜 클라이언트를 쓴다. */
@SpringBootTest
class PushDispatchIntegrationTest {

  private static final long WAIT_MILLIS = 5_000;
  private static final String PHONE = "https://fcm.googleapis.com/fcm/send/phone";
  private static final String LAPTOP = "https://updates.push.services.mozilla.com/wpush/v2/laptop";
  private static final String OTHERS = "https://web.push.apple.com/others";

  @MockitoBean private WebPushClient webPushClient;

  @Autowired private NotificationSender notificationSender;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = userRepository.save(User.builder().kakaoId("dispatch-me").nickname("dispatch-me").build());
    other =
        userRepository.save(
            User.builder().kakaoId("dispatch-other").nickname("dispatch-other").build());
    subscribe(me, PHONE, "phone-key", "phone-auth");
    subscribe(me, LAPTOP, "laptop-key", "laptop-auth");
    subscribe(other, OTHERS, "others-key", "others-auth");
    when(webPushClient.send(anyString(), anyString(), anyString(), any())).thenReturn(201);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("알림을 저장하면 받은 회원의 구독마다 한 건씩 보내고, 다른 회원에게는 보내지 않는다")
  void sendsToEveryDeviceOfRecipient() throws Exception {
    notificationSender.send(List.of(draft(me, 21L, "알림 제목", "알림 내용")));

    ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
    verify(webPushClient, timeout(WAIT_MILLIS))
        .send(eq(PHONE), eq("phone-key"), eq("phone-auth"), payload.capture());
    verify(webPushClient, timeout(WAIT_MILLIS))
        .send(eq(LAPTOP), eq("laptop-key"), eq("laptop-auth"), any());
    verify(webPushClient, after(300).never()).send(eq(OTHERS), anyString(), anyString(), any());

    Notification saved = notificationRepository.findAll().get(0);
    JsonNode json = objectMapper.readTree(payload.getValue());
    assertThat(json.get("notificationId").asLong()).isEqualTo(saved.getId());
    assertThat(json.get("type").asText()).isEqualTo("CAPSULE_OPENED");
    assertThat(json.get("referenceId").asLong()).isEqualTo(21L);
    assertThat(json.get("title").asText()).isEqualTo("알림 제목");
    assertThat(json.get("body").asText()).isEqualTo("알림 내용");
    assertThat(json.has("url")).isFalse();
  }

  @Test
  @DisplayName("내용이 없는 알림은 body 가 null 이다")
  void nullBodyWhenNoContent() throws Exception {
    notificationSender.send(List.of(draft(me, 21L, "제목만", null)));

    ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
    verify(webPushClient, timeout(WAIT_MILLIS)).send(eq(PHONE), any(), any(), payload.capture());
    assertThat(objectMapper.readTree(payload.getValue()).get("body").isNull()).isTrue();
  }

  @Test
  @DisplayName("푸시 서비스가 404 · 410 으로 응답한 구독은 지우고 나머지는 남긴다")
  void removesGoneSubscriptions() throws Exception {
    when(webPushClient.send(eq(PHONE), any(), any(), any())).thenReturn(410);
    when(webPushClient.send(eq(LAPTOP), any(), any(), any())).thenReturn(404);
    subscribe(me, "https://fcm.googleapis.com/fcm/send/tablet", "key", "auth");

    notificationSender.send(List.of(draft(me, 21L, "제목", null)));

    waitUntil(() -> pushSubscriptionRepository.count() == 2);
    assertThat(pushSubscriptionRepository.findAll())
        .extracting(PushSubscription::getEndpoint)
        .containsExactlyInAnyOrder("https://fcm.googleapis.com/fcm/send/tablet", OTHERS);
  }

  @Test
  @DisplayName("한 구독으로 보내다 실패해도 다른 구독으로는 보내고, 실패한 구독은 지우지 않는다")
  void failureDoesNotStopOthers() throws Exception {
    when(webPushClient.send(eq(PHONE), any(), any(), any()))
        .thenThrow(new WebPushException("연결 실패", null));

    notificationSender.send(List.of(draft(me, 21L, "제목", null)));

    verify(webPushClient, timeout(WAIT_MILLIS)).send(eq(LAPTOP), any(), any(), any());
    assertThat(pushSubscriptionRepository.findByEndpoint(PHONE)).isPresent();
  }

  @Test
  @DisplayName("알림을 저장한 트랜잭션이 롤백되면 푸시를 보내지 않는다")
  void rollbackSendsNothing() {
    transactionTemplate.executeWithoutResult(
        status -> {
          notificationSender.send(List.of(draft(me, 21L, "제목", null)));
          status.setRollbackOnly();
        });

    verify(webPushClient, after(500).never()).send(anyString(), anyString(), anyString(), any());
    assertThat(notificationRepository.count()).isZero();
  }

  private NotificationDraft draft(User user, Long capsuleId, String title, String content) {
    return new NotificationDraft(
        user.getId(), NotificationType.CAPSULE_OPENED, capsuleId, title, content, null);
  }

  private void subscribe(User user, String endpoint, String p256dh, String auth) {
    pushSubscriptionRepository.save(
        PushSubscription.builder().user(user).endpoint(endpoint).p256dh(p256dh).auth(auth).build());
  }

  private void waitUntil(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofMillis(WAIT_MILLIS));
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("조건이 " + WAIT_MILLIS + "ms 안에 만족되지 않았습니다.");
      }
      Thread.sleep(50);
    }
  }

  private void cleanUp() {
    notificationRepository.deleteAllInBatch();
    pushSubscriptionRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
