package com.momento.server.domain.push.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.push.dto.request.PushSubscriptionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushSubscriptionRequestTest {

  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("브라우저가 보내는 구독 형식이면 통과한다")
  void validSubscription() {
    assertThat(messages(request(ENDPOINT, keys("p256dh", "auth")))).isEmpty();
  }

  @Test
  @DisplayName("푸시 서비스가 아닌 주소는 거절한다")
  void rejectsUnsupportedEndpoint() {
    assertThat(messages(request("https://169.254.169.254/latest", keys("p256dh", "auth"))))
        .containsExactly("지원하지 않는 푸시 서비스 주소입니다.");
  }

  @Test
  @DisplayName("빈 endpoint 는 필수값 메시지 하나만 낸다")
  void blankEndpointReportsRequiredOnly() {
    assertThat(messages(request(" ", keys("p256dh", "auth")))).containsExactly("endpoint 는 필수입니다.");
  }

  @Test
  @DisplayName("keys 가 없거나 키 값이 비어 있으면 거절한다")
  void rejectsMissingKeys() {
    assertThat(messages(request(ENDPOINT, null))).containsExactly("keys 는 필수입니다.");
    assertThat(messages(request(ENDPOINT, keys("", null))))
        .containsExactlyInAnyOrder("keys.p256dh 는 필수입니다.", "keys.auth 는 필수입니다.");
  }

  private PushSubscriptionRequest request(String endpoint, PushSubscriptionRequest.Keys keys) {
    return new PushSubscriptionRequest(endpoint, null, keys);
  }

  private PushSubscriptionRequest.Keys keys(String p256dh, String auth) {
    return new PushSubscriptionRequest.Keys(p256dh, auth);
  }

  private List<String> messages(PushSubscriptionRequest request) {
    return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }
}
