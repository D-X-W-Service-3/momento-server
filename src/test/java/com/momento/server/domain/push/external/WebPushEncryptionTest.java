package com.momento.server.domain.push.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPushEncryptionTest {

  @Test
  @DisplayName("RFC 8291 부록 A 의 고정 값으로 암호화하면 RFC 에 적힌 결과와 바이트 단위로 같다")
  void matchesRfc8291Example() throws Exception {
    byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
    KeyPair applicationServer =
        new KeyPair(
            EcKeys.publicKey(
                b64(
                    "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
            EcKeys.privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));
    byte[] userAgentPublicKey =
        b64(
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    byte[] authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg");
    byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");

    byte[] message =
        WebPushEncryption.encrypt(
            userAgentPublicKey, authSecret, plaintext, applicationServer, salt);

    assertThat(EcKeys.encodeBase64Url(message))
        .isEqualTo(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
  }

  @Test
  @DisplayName("메시지마다 salt 와 임시 키가 달라 같은 내용도 결과가 다르다")
  void freshKeysPerMessage() throws Exception {
    byte[] userAgentPublicKey = EcKeys.encode((ECPublicKey) EcKeys.generate().getPublic());
    byte[] authSecret = new byte[16];
    byte[] plaintext = "같은 내용".getBytes(StandardCharsets.UTF_8);

    byte[] first = WebPushEncryption.encrypt(userAgentPublicKey, authSecret, plaintext);
    byte[] second = WebPushEncryption.encrypt(userAgentPublicKey, authSecret, plaintext);

    assertThat(first).isNotEqualTo(second);
    // salt 16 + 레코드 크기 4 + 키 길이 1 + 임시 공개키 65 + 본문 + 구분자 1 + GCM 태그 16
    assertThat(first).hasSize(16 + 4 + 1 + 65 + plaintext.length + 1 + 16);
  }

  private static byte[] b64(String value) {
    return EcKeys.decodeBase64Url(value);
  }
}
