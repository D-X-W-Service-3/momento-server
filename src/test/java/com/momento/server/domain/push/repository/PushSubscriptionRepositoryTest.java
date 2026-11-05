package com.momento.server.domain.push.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.push.entity.PushSubscription;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PushSubscriptionRepositoryTest {

  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/mine";
  private static final String OTHER_ENDPOINT = "https://fcm.googleapis.com/fcm/send/other";

  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private UserRepository userRepository;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    pushSubscriptionRepository.deleteAllInBatch();
    me = userRepository.save(User.builder().kakaoId("push-me").nickname("나").build());
    other = userRepository.save(User.builder().kakaoId("push-other").nickname("남").build());
  }

  @AfterEach
  void tearDown() {
    pushSubscriptionRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("endpoint 로 지울 때 내 구독만 지우고, 남의 구독은 endpoint 를 알아도 남는다")
  void deleteByEndpointOnlyRemovesOwnSubscription() {
    save(me, ENDPOINT);
    save(other, OTHER_ENDPOINT);

    assertThat(pushSubscriptionRepository.deleteByEndpointAndUserId(OTHER_ENDPOINT, me.getId()))
        .isZero();
    assertThat(pushSubscriptionRepository.deleteByEndpointAndUserId(ENDPOINT, me.getId()))
        .isEqualTo(1);

    assertThat(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).isEmpty();
    assertThat(pushSubscriptionRepository.findByEndpoint(OTHER_ENDPOINT)).isPresent();
  }

  @Test
  @DisplayName("회원의 구독을 모두 지우고, 다른 회원의 구독은 남는다")
  void deleteAllByUserIdKeepsOthers() {
    save(me, ENDPOINT);
    save(me, "https://updates.push.services.mozilla.com/wpush/v2/mine");
    save(other, OTHER_ENDPOINT);

    assertThat(pushSubscriptionRepository.deleteAllByUserId(me.getId())).isEqualTo(2);

    assertThat(pushSubscriptionRepository.findAll())
        .extracting(PushSubscription::getEndpoint)
        .containsExactly(OTHER_ENDPOINT);
  }

  @Test
  @DisplayName("여러 회원의 구독을 한 번에 찾고, 목록에 없는 회원의 구독은 빠진다")
  void findAllByUserIds() {
    User third = userRepository.save(User.builder().kakaoId("push-third").nickname("셋").build());
    save(me, ENDPOINT);
    save(other, OTHER_ENDPOINT);
    save(third, "https://web.push.apple.com/third");

    assertThat(pushSubscriptionRepository.findAllByUserIds(List.of(me.getId(), other.getId())))
        .extracting(PushSubscription::getEndpoint)
        .containsExactlyInAnyOrder(ENDPOINT, OTHER_ENDPOINT);
  }

  @Test
  @DisplayName("endpoint 목록으로 소유자와 관계없이 지운다")
  void deleteAllByEndpoints() {
    save(me, ENDPOINT);
    save(other, OTHER_ENDPOINT);
    save(me, "https://web.push.apple.com/kept");

    assertThat(pushSubscriptionRepository.deleteAllByEndpoints(List.of(ENDPOINT, OTHER_ENDPOINT)))
        .isEqualTo(2);

    assertThat(pushSubscriptionRepository.findAll())
        .extracting(PushSubscription::getEndpoint)
        .containsExactly("https://web.push.apple.com/kept");
  }

  private void save(User user, String endpoint) {
    pushSubscriptionRepository.save(
        PushSubscription.builder()
            .user(user)
            .endpoint(endpoint)
            .p256dh("key")
            .auth("auth")
            .build());
  }
}
