package com.momento.server.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** 알림 목록 조회·읽음 처리를 실제 필터·시큐리티까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {

  private static final Duration TOKEN_EXPIRY = Duration.ofDays(1);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TokenProvider tokenProvider;

  private User owner;
  private User other;
  private String ownerToken;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
    userRepository.deleteAll();

    owner = userRepository.save(kakaoUser("owner-kakao-id"));
    other = userRepository.save(kakaoUser("other-kakao-id"));
    ownerToken = tokenProvider.generateToken(owner, TOKEN_EXPIRY);
  }

  @AfterEach
  void tearDown() {
    notificationRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("알림 목록은 최신순이고 기본 페이지 크기(20) 안에서는 hasNext 가 false 다")
  void listReturnsLatestFirstWithinOnePage() throws Exception {
    List<Notification> saved = saveNotifications(owner, 3);

    mockMvc
        .perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifications.length()").value(3))
        .andExpect(jsonPath("$.data.notifications[0].notificationId").value(saved.get(2).getId()))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
  }

  @Test
  @DisplayName("페이지 크기를 넘으면 hasNext 가 true 이고 nextCursor 로 다음 페이지를 이어 받을 수 있다")
  void cursorPaginatesWithoutOverlap() throws Exception {
    List<Notification> saved = saveNotifications(owner, 25);

    String firstPageBody =
        mockMvc
            .perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.notifications.length()").value(20))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String nextCursor = com.jayway.jsonpath.JsonPath.read(firstPageBody, "$.data.nextCursor");
    assertThat(nextCursor).isEqualTo(String.valueOf(saved.get(5).getId()));

    mockMvc
        .perform(
            get("/v1/notifications")
                .param("cursor", nextCursor)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifications.length()").value(5))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.notifications[0].notificationId").value(saved.get(4).getId()));
  }

  @Test
  @DisplayName("다른 회원의 알림은 목록에 노출되지 않고 unreadCount 에도 포함되지 않는다")
  void listIsScopedToOwner() throws Exception {
    saveNotifications(owner, 2);
    saveNotifications(other, 5);

    mockMvc
        .perform(get("/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifications.length()").value(2))
        .andExpect(jsonPath("$.data.unreadCount").value(2));
  }

  @Test
  @DisplayName("알림을 읽음 처리하면 isRead·readAt 이 갱신되고, 재요청해도 최초 읽은 시각을 유지한다")
  void readNotificationIsIdempotent() throws Exception {
    Notification notification = saveNotifications(owner, 1).get(0);

    mockMvc
        .perform(
            patch("/v1/notifications/{id}/read", notification.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());

    Notification afterFirstRead =
        notificationRepository.findById(notification.getId()).orElseThrow();
    assertThat(afterFirstRead.isRead()).isTrue();
    LocalDateTime firstReadAt = afterFirstRead.getReadAt();
    assertThat(firstReadAt).isNotNull();

    mockMvc
        .perform(
            patch("/v1/notifications/{id}/read", notification.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());

    Notification afterSecondRead =
        notificationRepository.findById(notification.getId()).orElseThrow();
    assertThat(afterSecondRead.getReadAt()).isEqualTo(firstReadAt);
  }

  @Test
  @DisplayName("존재하지 않거나 다른 회원의 알림을 읽음 처리하면 404 다")
  void readNotificationRejectsNotOwned() throws Exception {
    Notification othersNotification = saveNotifications(other, 1).get(0);

    mockMvc
        .perform(
            patch("/v1/notifications/{id}/read", othersNotification.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

    mockMvc
        .perform(
            patch("/v1/notifications/{id}/read", 999_999L)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 호출하면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/v1/notifications")).andExpect(status().isUnauthorized());
  }

  private List<Notification> saveNotifications(User user, int count) {
    List<Notification> notifications =
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    Notification.builder()
                        .user(user)
                        .notificationType(NotificationType.CAPSULE_OPENED)
                        .title("타임캡슐이 열렸어요")
                        .content("기다리던 타임캡슐을 확인해보세요.")
                        .referenceId((long) i)
                        .build())
            .toList();
    return notificationRepository.saveAll(notifications);
  }

  private User kakaoUser(String kakaoId) {
    return User.builder().kakaoId(kakaoId).nickname("모멘토 친구").build();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
