package com.momento.server.domain.push.external;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시가 주고받는 P-256 키의 원시 바이트와 JDK 키 객체를 서로 바꾼다.
 *
 * <p>브라우저와 VAPID 는 공개키를 압축하지 않은 점({@code 0x04 || x || y}, 65바이트), 개인키를 스칼라 32바이트로 표현한다. 모두
 * base64url(패딩 없음)로 주고받는다.
 */
final class EcKeys {

  static final int PUBLIC_KEY_LENGTH = 65;
  private static final int COORDINATE_LENGTH = 32;
  private static final ECParameterSpec P256 = p256();

  private EcKeys() {}

  static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
    if (uncompressed.length != PUBLIC_KEY_LENGTH || uncompressed[0] != 0x04) {
      throw new GeneralSecurityException("P-256 공개키는 0x04 로 시작하는 65바이트여야 합니다.");
    }
    BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE_LENGTH));
    BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE_LENGTH, 65));
    return (ECPublicKey)
        KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
  }

  static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
    return (ECPrivateKey)
        KeyFactory.getInstance("EC")
            .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
  }

  static byte[] encode(ECPublicKey key) {
    byte[] out = new byte[PUBLIC_KEY_LENGTH];
    out[0] = 0x04;
    copyUnsigned(key.getW().getAffineX(), out, 1);
    copyUnsigned(key.getW().getAffineY(), out, 1 + COORDINATE_LENGTH);
    return out;
  }

  static KeyPair generate() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  static byte[] decodeBase64Url(String value) {
    return Base64.getUrlDecoder().decode(value);
  }

  static String encodeBase64Url(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  /** 좌표를 부호 없는 32바이트 big-endian 으로 맞춰 쓴다. BigInteger 는 앞에 0 바이트를 붙이거나 앞의 0 을 뗄 수 있다. */
  private static void copyUnsigned(BigInteger value, byte[] out, int offset) {
    byte[] bytes = value.toByteArray();
    int length = Math.min(bytes.length, COORDINATE_LENGTH);
    System.arraycopy(
        bytes, bytes.length - length, out, offset + COORDINATE_LENGTH - length, length);
  }

  private static ECParameterSpec p256() {
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      return parameters.getParameterSpec(ECParameterSpec.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("JDK 가 P-256 을 지원하지 않습니다.", e);
    }
  }
}
