package com.momento.server.domain.push.external;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 푸시 페이로드를 RFC 8291(Message Encryption for Web Push)의 {@code aes128gcm} 형식으로 암호화한다.
 *
 * <p>푸시 서비스(FCM 등)는 내용을 볼 수 없고, 구독한 브라우저만 자기 개인키로 풀 수 있다. 결과는 RFC 8188 헤더(salt · 레코드 크기 · 서버 임시
 * 공개키) 뒤에 암호문 한 레코드를 붙인 것이다.
 */
final class WebPushEncryption {

  private static final int SALT_LENGTH = 16;
  private static final int RECORD_SIZE = 4096;
  private static final int TAG_BITS = 128;
  private static final byte LAST_RECORD_DELIMITER = 0x02;
  private static final SecureRandom RANDOM = new SecureRandom();

  private WebPushEncryption() {}

  /** 메시지마다 새 임시 키쌍과 salt 를 만든다. 둘을 재사용하면 같은 키 · nonce 로 GCM 을 두 번 쓰게 된다. */
  static byte[] encrypt(byte[] userAgentPublicKey, byte[] authSecret, byte[] plaintext)
      throws GeneralSecurityException {
    byte[] salt = new byte[SALT_LENGTH];
    RANDOM.nextBytes(salt);
    return encrypt(userAgentPublicKey, authSecret, plaintext, EcKeys.generate(), salt);
  }

  /** 임시 키쌍과 salt 를 받는 버전. RFC 8291 부록 A 의 고정 값으로 결과를 검증하는 테스트가 쓴다. */
  static byte[] encrypt(
      byte[] userAgentPublicKey,
      byte[] authSecret,
      byte[] plaintext,
      KeyPair ephemeral,
      byte[] salt)
      throws GeneralSecurityException {
    byte[] serverPublicKey = EcKeys.encode((ECPublicKey) ephemeral.getPublic());

    KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
    agreement.init(ephemeral.getPrivate());
    agreement.doPhase(EcKeys.publicKey(userAgentPublicKey), true);
    byte[] ecdhSecret = agreement.generateSecret();

    // RFC 8291 3.4: auth secret 과 두 공개키를 섞어 입력 키(IKM)를 만든다
    byte[] keyInfo =
        concat(
            "WebPush: info".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0},
            userAgentPublicKey,
            serverPublicKey);
    byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

    // RFC 8188 2.2 · 2.3: salt 로 콘텐츠 키와 nonce 를 만든다
    byte[] contentKey = hkdf(salt, ikm, info("Content-Encoding: aes128gcm"), 16);
    byte[] nonce = hkdf(salt, ikm, info("Content-Encoding: nonce"), 12);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(contentKey, "AES"),
        new GCMParameterSpec(TAG_BITS, nonce));
    byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {LAST_RECORD_DELIMITER}));

    ByteBuffer header = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + serverPublicKey.length);
    header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublicKey.length).put(serverPublicKey);
    return concat(header.array(), ciphertext);
  }

  private static byte[] info(String label) {
    return concat(label.getBytes(StandardCharsets.US_ASCII), new byte[] {0});
  }

  /** HKDF-SHA256 (RFC 5869). 결과가 32바이트 이하라 expand 는 블록 하나로 끝난다. */
  private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length)
      throws GeneralSecurityException {
    byte[] prk = hmac(salt, ikm);
    byte[] okm = hmac(prk, concat(info, new byte[] {1}));
    return Arrays.copyOf(okm, length);
  }

  private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }
}
