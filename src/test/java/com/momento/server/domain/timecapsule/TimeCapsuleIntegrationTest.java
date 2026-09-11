package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 캡슐 API 를 실제 필터·시큐리티·트랜잭션까지 태워 검증한다.
 *
 * <p>테스트를 {@code @Transactional} 로 감싸지 않는다. {@code open-in-view} 가 꺼져 있어, 감싸면 운영에서 날 지연 로딩 오류가 테스트
 * 트랜잭션에 가려진다. 대신 매 테스트 전후로 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;

  private User owner;
  private String ownerToken;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("owner-kakao-id", "상래");
    ownerToken = tokenOf(owner);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("캡슐을 만들면 201 과 함께 편지를 쓸 수 있는 WRITING 상태로 생성된다")
  void createCapsule() throws Exception {
    String openAt = future();

    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("우리의 졸업 타임캡슐", "GROUP", "ALL_MEMBERS", openAt)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.capsuleId").isNumber())
        .andExpect(jsonPath("$.data.title").value("우리의 졸업 타임캡슐"))
        .andExpect(jsonPath("$.data.capsuleType").value("GROUP"))
        .andExpect(jsonPath("$.data.visibilityType").value("ALL_MEMBERS"))
        .andExpect(jsonPath("$.data.status").value("WRITING"))
        .andExpect(jsonPath("$.data.openAt").value(openAt))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
  }

  @Test
  @DisplayName("캡슐을 만들면 생성자가 OWNER 로 참여자에 함께 등록된다")
  void creatorBecomesOwnerMember() throws Exception {
    Long capsuleId = createCapsule(ownerToken);

    List<CapsuleMember> members = capsuleMemberRepository.findAll();
    assertThat(members).hasSize(1);

    CapsuleMember member = members.get(0);
    assertThat(member.getTimeCapsule().getId()).isEqualTo(capsuleId);
    assertThat(member.getUser().getId()).isEqualTo(owner.getId());
    assertThat(member.getRole()).isEqualTo(MemberRole.OWNER);
    assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    assertThat(member.getJoinedAt()).isNotNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCreateBodies")
  @DisplayName("요청 값이 잘못되면 400 으로 응답하고 캡슐을 만들지 않는다")
  void invalidCreateRequestIsRejected(String reason, String body) throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

    assertThat(timeCapsuleRepository.count()).isZero();
    assertThat(capsuleMemberRepository.count()).isZero();
  }

  static Stream<Arguments> invalidCreateBodies() throws JsonProcessingException {
    String past = DATE_TIME.format(LocalDateTime.now().minusDays(1));

    return Stream.of(
        Arguments.of("제목이 비어 있음", createBody("", "GROUP", "ALL_MEMBERS", future())),
        Arguments.of("제목이 50자를 넘음", createBody("가".repeat(51), "GROUP", "ALL_MEMBERS", future())),
        Arguments.of("공개 일시가 과거", createBody("캡슐", "GROUP", "ALL_MEMBERS", past)),
        Arguments.of("공개 일시가 빠짐", createBody("캡슐", "GROUP", "ALL_MEMBERS", null)),
        Arguments.of("캡슐 유형이 빠짐", createBody("캡슐", null, "ALL_MEMBERS", future())),
        Arguments.of("공개 범위가 빠짐", createBody("캡슐", "GROUP", null, future())),
        Arguments.of("enum 에 없는 캡슐 유형", createBody("캡슐", "TEAM", "ALL_MEMBERS", future())),
        Arguments.of("JSON 이 깨짐", "{\"title\":"));
  }

  @Test
  @DisplayName("인증 없이 캡슐을 만들 수 없다")
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("캡슐", "GROUP", "ALL_MEMBERS", future())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

    assertThat(timeCapsuleRepository.count()).isZero();
  }

  private Long createCapsule(String token) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/v1/time-capsules")
                    .header(AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("캡슐", "GROUP", "ALL_MEMBERS", future())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return JSON.readTree(response).path("data").path("capsuleId").asLong();
  }

  private static String createBody(
      String title, String capsuleType, String visibilityType, String openAt)
      throws JsonProcessingException {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("description", "설명");
    body.put("capsuleType", capsuleType);
    body.put("visibilityType", visibilityType);
    body.put("openAt", openAt);

    return JSON.writeValueAsString(body);
  }

  private static String future() {
    return DATE_TIME.format(LocalDateTime.now().plusDays(30));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private String tokenOf(User user) {
    return tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
