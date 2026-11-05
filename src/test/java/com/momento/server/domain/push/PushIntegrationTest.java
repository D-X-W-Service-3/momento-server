package com.momento.server.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.push.entity.PushSubscription;
import com.momento.server.domain.push.repository.PushSubscriptionRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import com.momento.server.global.common.property.VapidProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 웹 푸시 공개키 조회 · 구독 등록 · 해제를 실제 필터 · 시큐리티까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PushIntegrationTest {

  private static final Duration TOKEN_EXPIRY = Duration.ofDays(1);
  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc:APA91b?x=1&y=2";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private VapidProperties vapidProperties;

  private User me;
  private User other;
  private String myToken;
  private String otherToken;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = userRepository.save(User.builder().kakaoId("push-me").nickname("push-me").build());
    other =
        userRepository.save(User.builder().kakaoId("push-other").nickname("push-other").build());
    myToken = tokenProvider.generateToken(me, TOKEN_EXPIRY);
    otherToken = tokenProvider.generateToken(other, TOKEN_EXPIRY);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("공개키는 로그인 없이 조회되고 개인키는 응답에 없다")
  void publicKeyWithoutLogin() throws Exception {
    mockMvc
        .perform(get("/v1/push/vapid-public-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publicKey").value(vapidProperties.getPublicKey()))
        .andExpect(jsonPath("$.data.privateKey").doesNotExist());
  }

  @Test
  @DisplayName("구독을 등록하면 저장되고, 같은 endpoint 로 다시 보내면 새 행 없이 키만 바뀐다")
  void subscribeUpsertsByEndpoint() throws Exception {
    subscribe(myToken, ENDPOINT, "key-1", "auth-1").andExpect(status().isOk());
    subscribe(myToken, ENDPOINT, "key-2", "auth-2").andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.findAll())
        .singleElement()
        .satisfies(
            subscription -> {
              assertThat(subscription.getP256dh()).isEqualTo("key-2");
              assertThat(subscription.getAuth()).isEqualTo("auth-2");
            });
  }

  @Test
  @DisplayName("한 회원이 기기마다 여러 구독을 가질 수 있다")
  void multipleDevicesPerUser() throws Exception {
    subscribe(myToken, ENDPOINT, "key", "auth").andExpect(status().isOk());
    subscribe(myToken, "https://web.push.apple.com/QGuQyavXutnMH", "key", "auth")
        .andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.findAll()).hasSize(2);
  }

  @Test
  @DisplayName("같은 endpoint 가 다른 회원으로 들어오면 소유자가 그 회원으로 바뀐다")
  void subscribeTransfersOwnership() throws Exception {
    subscribe(myToken, ENDPOINT, "key", "auth").andExpect(status().isOk());
    subscribe(otherToken, ENDPOINT, "key", "auth").andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.findByEndpoint(ENDPOINT))
        .get()
        .extracting(subscription -> subscription.getUser().getId())
        .isEqualTo(other.getId());
    assertThat(pushSubscriptionRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("푸시 서비스가 아닌 주소로 구독하면 400 이고 저장되지 않는다")
  void rejectsUnsupportedEndpoint() throws Exception {
    subscribe(myToken, "https://169.254.169.254/latest/meta-data", "key", "auth")
        .andExpect(status().isBadRequest());

    assertThat(pushSubscriptionRepository.count()).isZero();
  }

  @Test
  @DisplayName("로그인하지 않으면 구독을 등록할 수 없다")
  void subscribeRequiresLogin() throws Exception {
    mockMvc
        .perform(
            post("/v1/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(ENDPOINT, "key", "auth")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("쿼리의 endpoint 로 내 구독을 지운다")
  void unsubscribeRemovesMine() throws Exception {
    subscribe(myToken, ENDPOINT, "key", "auth").andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/v1/push/subscriptions")
                .param("endpoint", ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(myToken)))
        .andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.count()).isZero();
  }

  @Test
  @DisplayName("남의 구독이나 없는 구독을 지우면 아무것도 지우지 않고 성공한다")
  void unsubscribeIgnoresOthersAndMissing() throws Exception {
    subscribe(otherToken, ENDPOINT, "key", "auth").andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/v1/push/subscriptions")
                .param("endpoint", ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, bearer(myToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/v1/push/subscriptions")
                .param("endpoint", "https://fcm.googleapis.com/fcm/send/none")
                .header(HttpHeaders.AUTHORIZATION, bearer(myToken)))
        .andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.findAll())
        .extracting(PushSubscription::getEndpoint)
        .containsExactly(ENDPOINT);
  }

  @Test
  @DisplayName("회원이 탈퇴하면 그 회원의 구독이 모두 지워지고 다른 회원의 구독은 남는다")
  void withdrawRemovesAllSubscriptions() throws Exception {
    subscribe(myToken, ENDPOINT, "key", "auth").andExpect(status().isOk());
    subscribe(myToken, "https://web.push.apple.com/QGuQyavXutnMH", "key", "auth")
        .andExpect(status().isOk());
    String othersEndpoint = "https://updates.push.services.mozilla.com/wpush/v2/other";
    subscribe(otherToken, othersEndpoint, "key", "auth").andExpect(status().isOk());

    mockMvc
        .perform(delete("/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(myToken)))
        .andExpect(status().isOk());

    assertThat(pushSubscriptionRepository.findAll())
        .extracting(PushSubscription::getEndpoint)
        .containsExactly(othersEndpoint);
  }

  private ResultActions subscribe(String token, String endpoint, String p256dh, String auth)
      throws Exception {
    return mockMvc.perform(
        post("/v1/push/subscriptions")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(endpoint, p256dh, auth)));
  }

  private String body(String endpoint, String p256dh, String auth) {
    return """
        {"endpoint": "%s", "expirationTime": null, "keys": {"p256dh": "%s", "auth": "%s"}}
        """
        .formatted(endpoint, p256dh, auth);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private void cleanUp() {
    pushSubscriptionRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
