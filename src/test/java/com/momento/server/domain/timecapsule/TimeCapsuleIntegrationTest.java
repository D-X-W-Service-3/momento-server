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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
  @DisplayName("캡슐을 만들면 201 과 캡슐 ID 만 돌려주고, 편지를 쓸 수 있는 WRITING 상태로 저장된다")
  void createCapsule() throws Exception {
    Map<String, Object> body = validBody();
    body.put("title", "우리의 졸업 타임캡슐");
    body.put("letterDeadlineDate", today().plusDays(10).toString());
    body.put("openDate", today().plusDays(30).toString());

    String response =
        mockMvc
            .perform(
                post("/v1/time-capsules")
                    .header(AUTHORIZATION, bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.capsuleId").isNumber())
            .andExpect(jsonPath("$.data.title").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long capsuleId = JSON.readTree(response).path("data").path("capsuleId").asLong();
    TimeCapsule saved = timeCapsuleRepository.findById(capsuleId).orElseThrow();
    assertThat(saved.getIconType()).isEqualTo("ICE_CREAM");
    assertThat(saved.getTitle()).isEqualTo("우리의 졸업 타임캡슐");
    assertThat(saved.getStatus()).isEqualTo(CapsuleStatus.WRITING);
    assertThat(saved.getLetterDeadlineAt())
        .isEqualTo(today().plusDays(10).atTime(LocalTime.of(23, 59, 59)));
    assertThat(saved.getOpenAt()).isEqualTo(today().plusDays(30).atStartOfDay());
  }

  @Test
  @DisplayName("이름은 20자, 설명은 45자까지 받는다")
  void titleAndDescriptionUpToLimitAreAccepted() throws Exception {
    Map<String, Object> body = validBody();
    body.put("title", "가".repeat(20));
    body.put("description", "가".repeat(45));

    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("오늘을 마감일로, 개봉일 2일 전을 마감일로 고를 수 있다")
  void deadlineBoundariesAreAccepted() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduleBody(today(), today().plusDays(2))))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidSchedules")
  @DisplayName("마감일이 오늘보다 이르거나 개봉일 2일 전보다 늦으면 400 이고 캡슐을 만들지 않는다")
  void invalidScheduleIsRejected(String reason, String body) throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CAPSULE_SCHEDULE"));

    assertThat(timeCapsuleRepository.count()).isZero();
    assertThat(capsuleMemberRepository.count()).isZero();
  }

  static Stream<Arguments> invalidSchedules() throws JsonProcessingException {
    return Stream.of(
        Arguments.of("마감일이 어제", scheduleBody(today().minusDays(1), today().plusDays(10))),
        Arguments.of("마감일이 개봉일 전날", scheduleBody(today().plusDays(9), today().plusDays(10))),
        Arguments.of("마감일이 개봉일과 같음", scheduleBody(today().plusDays(10), today().plusDays(10))),
        Arguments.of("마감일이 개봉일보다 늦음", scheduleBody(today().plusDays(11), today().plusDays(10))),
        Arguments.of("개봉일이 이미 지남", scheduleBody(today().minusDays(3), today().minusDays(1))));
  }

  @Test
  @DisplayName("나에게 쓰는 캡슐을 전체 공개로 만들면 201 이다")
  void selfCapsuleWithAllMembersIsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("나에게 쓰는 캡슐", "SELF", "ALL_MEMBERS")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.capsuleId").isNumber());

    TimeCapsule saved = timeCapsuleRepository.findAll().get(0);
    assertThat(saved.getCapsuleType()).isEqualTo(CapsuleType.SELF);
    assertThat(saved.getVisibilityType()).isEqualTo(VisibilityType.ALL_MEMBERS);
  }

  @Test
  @DisplayName("나에게 쓰는 캡슐은 전체 공개 외의 범위를 거절하고 캡슐을 만들지 않는다")
  void selfCapsuleRejectsOtherVisibilities() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("나에게 쓰는 캡슐", "SELF", "RECIPIENT_AND_AUTHOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SELF_CAPSULE_VISIBILITY"));

    assertThat(timeCapsuleRepository.findAll()).isEmpty();
    assertThat(capsuleMemberRepository.findAll()).isEmpty();
  }

  @ParameterizedTest(name = "{0} + {1} → 201")
  @CsvSource({
    "FRIEND, RECIPIENT_AND_AUTHOR",
    "FRIEND, ALL_MEMBERS",
    "GROUP,  RECIPIENT_AND_AUTHOR",
    "GROUP,  ALL_MEMBERS"
  })
  @DisplayName("친구·그룹 캡슐은 두 공개 범위를 모두 받는다")
  void otherCapsuleTypesAcceptEveryVisibility(String capsuleType, String visibilityType)
      throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("캡슐", capsuleType, visibilityType)))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest(name = "{0} → 400")
  @CsvSource({"PARTICIPANTS_ONLY", "RECIPIENT_ONLY"})
  @DisplayName("없어진 공개 범위 값으로는 캡슐을 만들 수 없다")
  void removedVisibilityIsRejected(String visibilityType) throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("캡슐", "GROUP", visibilityType)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

    assertThat(timeCapsuleRepository.findAll()).isEmpty();
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
    return Stream.of(
        Arguments.of("이름이 비어 있음", createBody("", "GROUP", "ALL_MEMBERS")),
        Arguments.of("이름이 20자를 넘음", createBody("가".repeat(21), "GROUP", "ALL_MEMBERS")),
        Arguments.of("설명이 45자를 넘음", bodyWith("description", "가".repeat(46))),
        Arguments.of("아이콘이 빠짐", bodyWith("iconType", null)),
        Arguments.of("아이콘이 비어 있음", bodyWith("iconType", " ")),
        Arguments.of("마감일이 빠짐", bodyWith("letterDeadlineDate", null)),
        Arguments.of("개봉일이 빠짐", bodyWith("openDate", null)),
        Arguments.of("개봉일이 날짜 형식이 아님", bodyWith("openDate", "2027/12/12")),
        Arguments.of("캡슐 유형이 빠짐", createBody("캡슐", null, "ALL_MEMBERS")),
        Arguments.of("공개 범위가 빠짐", createBody("캡슐", "GROUP", null)),
        Arguments.of("enum 에 없는 캡슐 유형", createBody("캡슐", "TEAM", "ALL_MEMBERS")),
        Arguments.of("JSON 이 깨짐", "{\"title\":"));
  }

  @Test
  @DisplayName("인증 없이 캡슐을 만들 수 없다")
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("캡슐", "GROUP", "ALL_MEMBERS")))
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
    "RECIPIENT_AND_AUTHOR, RECIPIENT,   true",
    "RECIPIENT_AND_AUTHOR, PARTICIPANT, true",
    "RECIPIENT_AND_AUTHOR, OWNER,       true",
    "ALL_MEMBERS,       RECIPIENT,   true",
    "ALL_MEMBERS,       PARTICIPANT, true",
    "ALL_MEMBERS,       OWNER,       true"
  })
  @DisplayName("열린 캡슐의 편지함은 공개 범위와 관계없이 멤버 전원이 들어가고, 참여자 목록은 OWNER 만 볼 수 있다")
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
                    .content(createBody("캡슐", "GROUP", "ALL_MEMBERS")))
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

  /** 그대로 보내면 201 이 나는 요청 본문. 테스트마다 바꿀 값만 덮어쓴다. */
  private static Map<String, Object> validBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("iconType", "ICE_CREAM");
    body.put("title", "캡슐");
    body.put("description", "설명");
    body.put("capsuleType", "GROUP");
    body.put("visibilityType", "ALL_MEMBERS");
    body.put("letterDeadlineDate", today().plusDays(10).toString());
    body.put("openDate", today().plusDays(30).toString());
    return body;
  }

  private static String createBody(String title, String capsuleType, String visibilityType)
      throws JsonProcessingException {
    Map<String, Object> body = validBody();
    body.put("title", title);
    body.put("capsuleType", capsuleType);
    body.put("visibilityType", visibilityType);
    return JSON.writeValueAsString(body);
  }

  private static String bodyWith(String key, Object value) throws JsonProcessingException {
    Map<String, Object> body = validBody();
    body.put(key, value);
    return JSON.writeValueAsString(body);
  }

  private static String scheduleBody(LocalDate letterDeadlineDate, LocalDate openDate)
      throws JsonProcessingException {
    Map<String, Object> body = validBody();
    body.put("letterDeadlineDate", letterDeadlineDate.toString());
    body.put("openDate", openDate.toString());
    return JSON.writeValueAsString(body);
  }

  /** 서비스는 Clock 빈(시스템 기본 시간대)으로 오늘을 판단하므로 테스트도 같은 기준을 쓴다. */
  private static LocalDate today() {
    return LocalDate.now();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(
      CapsuleStatus status, VisibilityType visibility, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(visibility)
            .status(status)
            .letterDeadlineAt(LocalDateTime.now().plusDays(10))
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
