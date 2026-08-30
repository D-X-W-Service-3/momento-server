package com.momento.server.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.auth.external.KakaoApiClient;
import com.momento.server.domain.auth.external.dto.KakaoUserResponse;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 카카오 API 만 대체하고 로그인 흐름을 실제 필터/시큐리티까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class KakaoLoginIntegrationTest {

  private static final long KAKAO_ID = 1234567890L;
  private static final String NICKNAME = "모모";
  private static final String PROFILE_IMAGE_URL = "https://k.kakaocdn.net/dn/profile.jpg";
  private static final String LOGIN_BODY = "{\"accessToken\":\"kakao-access-token\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @MockitoBean private KakaoApiClient kakaoApiClient;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    given(kakaoApiClient.getUserInfo(anyString()))
        .willReturn(
            new KakaoUserResponse(
                KAKAO_ID,
                new KakaoUserResponse.KakaoAccount(
                    new KakaoUserResponse.Profile(NICKNAME, PROFILE_IMAGE_URL))));
  }

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("첫 로그인은 회원을 생성하고 발급된 토큰으로 인증된 요청을 보낼 수 있다")
  void loginCreatesUserAndIssuesUsableToken() throws Exception {
    String accessToken = login(true);

    User saved = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    assertThat(saved.getNickname()).isEqualTo(NICKNAME);
    assertThat(saved.getProfileImageUrl()).isEqualTo(PROFILE_IMAGE_URL);
    assertThat(saved.isNotificationEnabled()).isTrue();
    assertThat(saved.getDeletedAt()).isNull();

    mockMvc
        .perform(post("/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/v1/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("이미 가입한 카카오 계정으로 다시 로그인하면 회원을 새로 만들지 않는다")
  void secondLoginReusesUser() throws Exception {
    login(true);
    login(false);

    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("탈퇴한 회원이 같은 카카오 계정으로 로그인하면 계정이 복구된다")
  void loginRestoresWithdrawnUser() throws Exception {
    login(true);

    User user = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    user.withdraw();
    userRepository.saveAndFlush(user);

    login(false);

    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow().getDeletedAt())
        .isNull();
  }

  @Test
  @DisplayName("카카오 액세스 토큰이 비어 있으면 400 으로 응답한다")
  void blankAccessTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessToken\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("카카오가 토큰을 거부하면 401 로 응답하고 회원을 만들지 않는다")
  void invalidKakaoTokenIsRejected() throws Exception {
    given(kakaoApiClient.getUserInfo(anyString()))
        .willThrow(new FeignException.Unauthorized("unauthorized", kakaoRequest(), null, Map.of()));

    mockMvc
        .perform(post("/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_KAKAO_TOKEN"));

    assertThat(userRepository.count()).isZero();
  }

  @Test
  @DisplayName("카카오 서버 오류는 인증 실패와 구분해 502 로 응답한다")
  void kakaoServerErrorIsNotTreatedAsAuthFailure() throws Exception {
    given(kakaoApiClient.getUserInfo(anyString()))
        .willThrow(
            new FeignException.InternalServerError("kakao down", kakaoRequest(), null, Map.of()));

    mockMvc
        .perform(post("/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("KAKAO_SERVER_ERROR"));

    assertThat(userRepository.count()).isZero();
  }

  private Request kakaoRequest() {
    return Request.create(
        Request.HttpMethod.GET,
        "https://kapi.kakao.com/v2/user/me",
        Map.of(),
        null,
        StandardCharsets.UTF_8);
  }

  private String login(boolean expectedNewUser) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isNewUser").value(expectedNewUser))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode accessToken = objectMapper.readTree(body).path("data").path("accessToken");
    assertThat(accessToken.asText()).isNotBlank();

    return accessToken.asText();
  }
}
