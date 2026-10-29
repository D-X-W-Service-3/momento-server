package com.momento.server.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.auth.external.KakaoApiClient;
import com.momento.server.domain.auth.external.KakaoAuthClient;
import com.momento.server.domain.auth.external.dto.KakaoTokenResponse;
import com.momento.server.domain.auth.external.dto.KakaoUserResponse;
import com.momento.server.domain.user.entity.NotificationSettings;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.domain.user.service.UserService;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;

/** 카카오 API 만 대체하고 로그인 흐름을 실제 필터/시큐리티까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class KakaoLoginIntegrationTest {

  private static final long KAKAO_ID = 1234567890L;
  private static final String NICKNAME = "모모";
  private static final String TEMPORARY_NICKNAME = "모멘토친구\\d{6}";
  private static final String PROFILE_IMAGE_URL = "https://k.kakaocdn.net/dn/profile.jpg";
  private static final String REDIRECT_URI = "http://localhost:3000/auth/kakao/callback";
  private static final String LOGIN_BODY =
      "{\"code\":\"kakao-authorization-code\",\"redirectUri\":\"" + REDIRECT_URI + "\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private KakaoAuthClient kakaoAuthClient;
  @MockitoBean private KakaoApiClient kakaoApiClient;
  @MockitoSpyBean private UserService userService;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    given(kakaoAuthClient.issueToken(any()))
        .willReturn(new KakaoTokenResponse("kakao-access-token"));
    givenKakaoUser(KAKAO_ID, NICKNAME, PROFILE_IMAGE_URL);
  }

  private void givenKakaoUser(long kakaoId, String nickname, String profileImageUrl) {
    KakaoUserResponse.KakaoAccount account =
        nickname == null && profileImageUrl == null
            ? null
            : new KakaoUserResponse.KakaoAccount(
                new KakaoUserResponse.Profile(nickname, profileImageUrl));
    given(kakaoApiClient.getUserInfo(anyString()))
        .willReturn(new KakaoUserResponse(kakaoId, account));
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
    assertThat(saved.getNickname()).matches(TEMPORARY_NICKNAME).isNotEqualTo(NICKNAME);
    assertThat(saved.getProfileImageUrl()).isEqualTo(PROFILE_IMAGE_URL);
    assertThat(saved.getNotificationSettings())
        .usingRecursiveComparison()
        .isEqualTo(NotificationSettings.defaults());
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
  @DisplayName("카카오 닉네임에 동의하지 않아도 임시 닉네임으로 가입한다")
  void loginWithoutKakaoNickname() throws Exception {
    givenKakaoUser(KAKAO_ID, null, null);

    login(true);

    User saved = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    assertThat(saved.getNickname()).matches(TEMPORARY_NICKNAME);
    assertThat(saved.getProfileImageUrl()).isNull();
  }

  @Test
  @DisplayName("카카오 닉네임이 같은 두 사람이 가입해도 닉네임이 겹치지 않는다")
  void twoUsersGetDifferentNicknames() throws Exception {
    login(true);
    givenKakaoUser(KAKAO_ID + 1, NICKNAME, PROFILE_IMAGE_URL);
    login(true);

    assertThat(userRepository.findAll())
        .extracting(User::getNickname)
        .hasSize(2)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("가입이 닉네임 UNIQUE 에 걸려도 한 번 더 시도해 로그인에 성공한다")
  void retriesRegisterOnUniqueViolation() throws Exception {
    willThrow(new DataIntegrityViolationException("임시 닉네임 중복"))
        .willCallRealMethod()
        .given(userService)
        .register(anyString(), any());

    login(true);

    assertThat(userRepository.findByKakaoId(String.valueOf(KAKAO_ID))).isPresent();
    then(userService).should(times(2)).register(anyString(), any());
  }

  @Test
  @DisplayName("같은 카카오 계정의 다른 요청이 먼저 가입시켰으면 그 회원으로 로그인한다")
  void usesAccountCreatedByConcurrentLogin() throws Exception {
    TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
    otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    willAnswer(
            invocation -> {
              // 다른 요청이 먼저 가입시켜 커밋까지 마친 상황이다. 이 가입의 트랜잭션은 롤백되므로 따로 커밋한다.
              otherRequest.executeWithoutResult(
                  tx ->
                      userRepository.save(
                          User.builder()
                              .kakaoId(invocation.getArgument(0))
                              .nickname("먼저가입")
                              .build()));
              throw new DataIntegrityViolationException("카카오 ID 중복");
            })
        .given(userService)
        .register(anyString(), any());

    login(true);

    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow().getNickname())
        .isEqualTo("먼저가입");
    then(userService).should(times(1)).register(anyString(), any());
  }

  @Test
  @DisplayName("탈퇴한 회원이 같은 카카오 계정으로 로그인하면 계정이 복구되고, 닉네임 · 사진은 탈퇴 전 그대로다")
  void loginRestoresWithdrawnUser() throws Exception {
    login(true);

    User user = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    user.updateNickname("직접정한닉네임");
    user.withdraw();
    userRepository.saveAndFlush(user);
    givenKakaoUser(KAKAO_ID, "카카오에서바꾼닉네임", "https://k.kakaocdn.net/dn/changed.jpg");

    login(false);

    User restored = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(restored.getNickname()).isEqualTo("직접정한닉네임");
    assertThat(restored.getProfileImageUrl()).isEqualTo(PROFILE_IMAGE_URL);
  }

  @Test
  @DisplayName("탈퇴 API 로 탈퇴한 뒤 같은 카카오 계정으로 로그인하면 같은 회원으로 복구되고 새 토큰을 쓸 수 있다")
  void loginRestoresUserWithdrawnByApi() throws Exception {
    String accessToken = login(true);
    User user = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();

    mockMvc
        .perform(delete("/v1/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    assertThat(userRepository.findById(user.getId()).orElseThrow().isWithdrawn()).isTrue();

    String restoredToken = login(false);

    User restored = userRepository.findById(user.getId()).orElseThrow();
    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(restored.isWithdrawn()).isFalse();
    assertThat(restored.getNickname()).isEqualTo(user.getNickname());
    mockMvc
        .perform(get("/v1/users/me").header("Authorization", "Bearer " + restoredToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(user.getId()));
  }

  @Test
  @DisplayName("탈퇴한 회원의 토큰은 서명이 유효해도 401 로 거절된다")
  void withdrawnUserTokenIsRejected() throws Exception {
    String accessToken = login(true);

    User user = userRepository.findByKakaoId(String.valueOf(KAKAO_ID)).orElseThrow();
    user.withdraw();
    userRepository.saveAndFlush(user);

    mockMvc
        .perform(post("/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("카카오 인가 코드가 비어 있으면 400 으로 응답한다")
  void blankAuthorizationCodeIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("Redirect URI 가 비어 있으면 400 으로 응답한다")
  void blankRedirectUriIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"kakao-authorization-code\",\"redirectUri\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("요청으로 받은 Redirect URI 를 그대로 카카오 토큰 교환에 사용한다")
  void redirectUriFromRequestIsForwardedToKakao() throws Exception {
    String frontRedirectUri = "https://momento.app/auth/kakao/callback";

    mockMvc
        .perform(
            post("/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"kakao-authorization-code\",\"redirectUri\":\""
                        + frontRedirectUri
                        + "\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<MultiValueMap<String, String>> captor =
        ArgumentCaptor.forClass(MultiValueMap.class);
    verify(kakaoAuthClient).issueToken(captor.capture());
    assertThat(captor.getValue().getFirst("redirect_uri")).isEqualTo(frontRedirectUri);
  }

  @Test
  @DisplayName("카카오가 인가 코드를 거부하면 401 로 응답하고 회원을 만들지 않는다")
  void invalidAuthorizationCodeIsRejected() throws Exception {
    given(kakaoAuthClient.issueToken(any()))
        .willThrow(new FeignException.BadRequest("invalid_grant", kakaoRequest(), null, Map.of()));

    mockMvc
        .perform(post("/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_KAKAO_CODE"));

    assertThat(userRepository.count()).isZero();
  }

  @Test
  @DisplayName("교환한 토큰을 카카오가 거부하면 401 로 응답하고 회원을 만들지 않는다")
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
    given(kakaoAuthClient.issueToken(any()))
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
