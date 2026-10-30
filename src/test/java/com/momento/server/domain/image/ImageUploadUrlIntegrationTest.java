package com.momento.server.domain.image;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 이미지 업로드 URL 발급 API 를 실제 시큐리티 필터까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ImageUploadUrlIntegrationTest {

  private static final String UUID_PATTERN = "[0-9a-f-]{36}";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TokenProvider tokenProvider;

  private User me;
  private String token;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    me = userRepository.save(User.builder().kakaoId("kakao-me").nickname("서현").build());
    token = tokenProvider.generateToken(me, Duration.ofMinutes(10));
  }

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  private ResultActions issue(String body) throws Exception {
    return mockMvc.perform(
        post("/v1/images/presigned-urls")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("파일 순서대로 내 경로의 key · 그 key 로 올릴 주소 · 만료 시각을 준다")
  void issueMemoryUploadUrls() throws Exception {
    String prefix = "memories/" + me.getId() + "/" + UUID_PATTERN + "\\.";

    issue(
            """
            {"target": "MEMORY", "files": [{"contentType": "image/jpeg"}, {"contentType": "image/png"}]}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.uploads.length()").value(2))
        .andExpect(jsonPath("$.data.uploads[0].imageKey").value(matchesPattern(prefix + "jpg")))
        .andExpect(jsonPath("$.data.uploads[1].imageKey").value(matchesPattern(prefix + "png")))
        .andExpect(
            jsonPath("$.data.uploads[0].uploadUrl")
                .value(containsString("/memories/" + me.getId() + "/")))
        .andExpect(
            jsonPath("$.data.uploads[0].uploadUrl").value(containsString("X-Amz-Signature=")))
        .andExpect(jsonPath("$.data.uploads[0].expiresAt").isString());
  }

  @Test
  @DisplayName("PROFILE 용도는 profiles 경로의 key 를 준다")
  void issueProfileUploadUrl() throws Exception {
    issue(
            """
            {"target": "PROFILE", "files": [{"contentType": "image/webp"}]}
            """)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.uploads[0].imageKey")
                .value(matchesPattern("profiles/" + me.getId() + "/" + UUID_PATTERN + "\\.webp")));
  }

  @Test
  @DisplayName("지원하지 않는 형식이 하나라도 있으면 400 INVALID_INPUT_VALUE 와 형식 안내 메시지다")
  void rejectsUnsupportedType() throws Exception {
    for (String contentType : List.of("image/gif", "IMAGE/JPEG", "")) {
      issue(
              """
              {"target": "MEMORY", "files": [{"contentType": "image/jpeg"}, {"contentType": "%s"}]}
              """
                  .formatted(contentType))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
          .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 형식입니다."));
    }
  }

  @Test
  @DisplayName("용도가 없거나 모르는 값이면 400 INVALID_INPUT_VALUE 다")
  void rejectsInvalidTarget() throws Exception {
    for (String body :
        List.of(
            "{\"files\": [{\"contentType\": \"image/jpeg\"}]}",
            "{\"target\": \"LETTER\", \"files\": [{\"contentType\": \"image/jpeg\"}]}")) {
      issue(body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
  }

  @Test
  @DisplayName("파일은 1~8개만 받는다")
  void fileCountIsLimited() throws Exception {
    String nine =
        IntStream.rangeClosed(1, 9)
            .mapToObj(i -> "{\"contentType\": \"image/jpeg\"}")
            .collect(Collectors.joining(","));

    for (String body :
        List.of(
            "{\"target\": \"MEMORY\", \"files\": []}",
            "{\"target\": \"MEMORY\"}",
            "{\"target\": \"MEMORY\", \"files\": [" + nine + "]}")) {
      issue(body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
  }

  @Test
  @DisplayName("토큰 없이 요청하면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/v1/images/presigned-urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"target\": \"MEMORY\", \"files\": [{\"contentType\": \"image/jpeg\"}]}"))
        .andExpect(status().isUnauthorized());
  }
}
