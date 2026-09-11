package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

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

  // ---------- 생성 ----------

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

  // ---------- 상세 조회 ----------

  @Test
  @DisplayName("OWNER 가 상세를 조회하면 내 역할과 볼 수 있는 범위를 함께 받는다")
  void ownerGetsDetail() throws Exception {
    Long capsuleId = createCapsule(ownerToken);

    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsuleId)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsuleId").value(capsuleId))
        .andExpect(jsonPath("$.data.status").value("WRITING"))
        .andExpect(jsonPath("$.data.myRole").value("OWNER"))
        .andExpect(jsonPath("$.data.memberCount").value(1))
        .andExpect(jsonPath("$.data.letterCount").value(0))
        .andExpect(jsonPath("$.data.canViewMemberList").value(true))
        .andExpect(jsonPath("$.data.canViewLetters").value(false));
  }

  @Test
  @DisplayName("참여자 수는 ACTIVE 만, 편지 수는 제출됐고 삭제되지 않은 것만 센다")
  void countsOnlyActiveMembersAndSubmittedLetters() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS, null);
    User participant = saveUser("participant-kakao-id", "민수");
    User leaver = saveUser("leaver-kakao-id", "지수");
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    saveMember(capsule, leaver, MemberRole.PARTICIPANT, MemberStatus.LEFT);
    saveLetter(capsule, owner, LetterStatus.SUBMITTED, null);
    saveLetter(capsule, participant, LetterStatus.DRAFT, null);
    saveLetter(capsule, leaver, LetterStatus.SUBMITTED, LocalDateTime.now());

    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsule.getId())
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberCount").value(2))
        .andExpect(jsonPath("$.data.letterCount").value(1));
  }

  @Test
  @DisplayName("참여하지 않은 캡슐, 없는 캡슐, 삭제된 캡슐은 서로 구분할 수 없는 같은 404 를 받는다")
  void inaccessibleCapsulesLookTheSame() throws Exception {
    Long capsuleId = createCapsule(ownerToken);
    TimeCapsule deleted =
        saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS, LocalDateTime.now());
    saveMember(deleted, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    String strangerToken = tokenOf(saveUser("stranger-kakao-id", "민수"));

    String notMember = notFoundBody(capsuleId, strangerToken);
    String missing = notFoundBody(Long.MAX_VALUE, ownerToken);
    String deletedCapsule = notFoundBody(deleted.getId(), ownerToken);

    assertThat(JSON.readTree(notMember).path("code").asText()).isEqualTo("CAPSULE_NOT_FOUND");
    assertThat(missing).isEqualTo(notMember);
    assertThat(deletedCapsule).isEqualTo(notMember);
  }

  @Test
  @DisplayName("캡슐에서 나간 회원은 더 이상 상세를 볼 수 없다")
  void leftMemberCannotSeeDetail() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS, null);
    User leaver = saveUser("leaver-kakao-id", "지수");
    saveMember(capsule, leaver, MemberRole.PARTICIPANT, MemberStatus.LEFT);

    String body = notFoundBody(capsule.getId(), tokenOf(leaver));

    assertThat(JSON.readTree(body).path("code").asText()).isEqualTo("CAPSULE_NOT_FOUND");
  }

  @ParameterizedTest(name = "{0} 캡슐의 {1} → {2}")
  @CsvSource({
    "RECIPIENT_ONLY,    RECIPIENT,   true",
    "RECIPIENT_ONLY,    PARTICIPANT, false",
    "RECIPIENT_ONLY,    OWNER,       true",
    "PARTICIPANTS_ONLY, PARTICIPANT, true",
    "PARTICIPANTS_ONLY, RECIPIENT,   false",
    "PARTICIPANTS_ONLY, OWNER,       true",
    "ALL_MEMBERS,       RECIPIENT,   true",
    "ALL_MEMBERS,       PARTICIPANT, true"
  })
  @DisplayName("열린 캡슐의 편지는 공개 범위에 든 역할만 볼 수 있고, 참여자 목록은 OWNER 만 볼 수 있다")
  void openedCapsuleVisibilityFollowsRole(
      VisibilityType visibility, MemberRole role, boolean canViewLetters) throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, visibility, null);
    User viewer = role == MemberRole.OWNER ? owner : saveUser("viewer-kakao-id", "민수");
    saveMember(capsule, viewer, role, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsule.getId())
                .header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.myRole").value(role.name()))
        .andExpect(jsonPath("$.data.canViewLetters").value(canViewLetters))
        .andExpect(jsonPath("$.data.canViewMemberList").value(role == MemberRole.OWNER));
  }

  @Test
  @DisplayName("경로의 캡슐 ID 가 숫자가 아니면 500 이 아니라 400 으로 응답한다")
  void nonNumericCapsuleIdIsBadRequest() throws Exception {
    mockMvc
        .perform(get("/v1/time-capsules/abc").header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("인증 없이 상세를 조회할 수 없다")
  void detailRequiresAuthentication() throws Exception {
    Long capsuleId = createCapsule(ownerToken);

    mockMvc
        .perform(get("/v1/time-capsules/{capsuleId}", capsuleId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  // ---------- 도우미 ----------

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

  private String notFoundBody(Long capsuleId, String token) throws Exception {
    return mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsuleId).header(AUTHORIZATION, bearer(token)))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
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

  private TimeCapsule saveCapsule(
      CapsuleStatus status, VisibilityType visibility, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(visibility)
            .status(status)
            .openAt(LocalDateTime.now().plusDays(30))
            .deletedAt(deletedAt)
            .build());
  }

  private void saveMember(TimeCapsule capsule, User user, MemberRole role, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(LocalDateTime.now())
            .build());
  }

  /** 편지 도메인에는 아직 Repository 가 없어 EntityManager 로 직접 넣는다. */
  private void saveLetter(
      TimeCapsule capsule, User author, LetterStatus status, LocalDateTime deletedAt) {
    transactionTemplate.executeWithoutResult(
        tx ->
            entityManager.persist(
                Letter.builder()
                    .timeCapsule(capsule)
                    .author(author)
                    .content("편지")
                    .status(status)
                    .deletedAt(deletedAt)
                    .build()));
  }

  private String tokenOf(User user) {
    return tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private void cleanUp() {
    transactionTemplate.executeWithoutResult(
        tx -> entityManager.createQuery("delete from Letter").executeUpdate());
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
