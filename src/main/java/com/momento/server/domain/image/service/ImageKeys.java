package com.momento.server.domain.image.service;

import com.momento.server.domain.image.entity.ImageTarget;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 업로드 이미지의 S3 key 규칙. {@code {용도 경로}/{회원 ID}/{UUID}.{확장자}} 이다. 예: {@code
 * memories/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg}
 *
 * <p>key 에 회원 ID 를 넣어, 등록할 때 key 만 보고 내가 발급받은 이미지인지 확인한다. 파일 이름은 서버가 UUID 로 정해 클라이언트가 보낸 이름이 경로에
 * 섞이지 않게 한다.
 */
public final class ImageKeys {

  /** 허용하는 Content-Type 과 key 에 붙일 확장자. heic 는 아이폰 기본 사진 형식이다. */
  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/heic", "heic");

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private ImageKeys() {}

  /** 새 업로드에 쓸 key. Content-Type 은 요청 검증에서 허용 목록으로 이미 걸렀다. */
  public static String newKey(ImageTarget target, Long userId, String contentType) {
    String extension = EXTENSIONS.get(contentType);
    if (extension == null) {
      throw new IllegalArgumentException("허용하지 않는 Content-Type: " + contentType);
    }
    return target.getDirectory() + "/" + userId + "/" + UUID.randomUUID() + "." + extension;
  }

  /** 이 회원에게 이 용도로 발급한 모양의 key 인지. */
  public static boolean isOwnedBy(ImageTarget target, String key, Long userId) {
    if (key == null) {
      return false;
    }
    Matcher matcher =
        Pattern.compile(
                Pattern.quote(target.getDirectory())
                    + "/(\\d+)/"
                    + UUID_PATTERN
                    + "\\.(jpg|png|webp|heic)")
            .matcher(key);
    return matcher.matches() && matcher.group(1).equals(String.valueOf(userId));
  }
}
