package com.momento.server.global.common.property;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VapidPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("base64url 키와 mailto · https 연락처는 통과한다")
  void validProperties() {
    assertThat(violations(properties("BIz1Zb-_9", "YrPXSq_-0", "mailto:dev@momento.local")))
        .isEmpty();
    assertThat(violations(properties("BIz1Zb", "YrPXSq", "https://github.com/team"))).isEmpty();
  }

  @Test
  @DisplayName("환경변수가 빠져 placeholder 문자열이 그대로 들어오면 세 값 모두 거절한다")
  void rejectsUnresolvedPlaceholders() {
    VapidProperties properties =
        properties("${VAPID_PUBLIC_KEY}", "${VAPID_PRIVATE_KEY}", "${VAPID_SUBJECT}");

    assertThat(violations(properties))
        .containsExactlyInAnyOrder("publicKey", "privateKey", "subject");
  }

  private VapidProperties properties(String publicKey, String privateKey, String subject) {
    VapidProperties properties = new VapidProperties();
    properties.setPublicKey(publicKey);
    properties.setPrivateKey(privateKey);
    properties.setSubject(subject);
    return properties;
  }

  private List<String> violations(VapidProperties properties) {
    return validator.validate(properties).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .toList();
  }
}
