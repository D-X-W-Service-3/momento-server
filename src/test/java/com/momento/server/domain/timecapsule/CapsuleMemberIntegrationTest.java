package com.momento.server.domain.timecapsule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.letter.repository.LetterRepository;
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
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 캡슐 멤버 목록 · 내보내기 API 를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CapsuleMemberIntegrationTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;

  private User owner;
  private User participant;
  private User recipient;
  private TimeCapsule capsule;
  private CapsuleMember ownerMember;
  private CapsuleMember participantMember;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("member-owner", "방장");
    participant = saveUser("member-participant", "참여자");
    recipient = saveUser("member-recipient", "수신자");
    capsule = saveCapsule(CapsuleStatus.WRITING, FAR_FUTURE.minusDays(10));
    participantMember = join(capsule, participant, MemberRole.PARTICIPANT, 3);
    ownerMember = join(capsule, owner, MemberRole.OWNER, 1);
    join(capsule, recipient, MemberRole.RECIPIENT, 2);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  // ---------- 목록 ----------

  @Test
  @DisplayName("참여자 목록은 방장을 맨 앞에 두고 참여한 순서다. 수신자 · 내보낸 멤버 · 다른 캡슐 멤버는 빠진다")
  void listsParticipantsOwnerFirst() throws Exception {
    CapsuleMember late = join(capsule, saveUser("member-late", "나중"), MemberRole.PARTICIPANT, 0);
    CapsuleMember removed =
        join(capsule, saveUser("member-removed", "내보냄"), MemberRole.PARTICIPANT, 5);
    removed.remove();
    capsuleMemberRepository.save(removed);
    TimeCapsule other = saveCapsule(CapsuleStatus.WRITING, FAR_FUTURE.minusDays(10));
    join(other, saveUser("member-other", "남"), MemberRole.PARTICIPANT, 9);

    getMembers(owner, "PARTICIPANT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.count").value(3))
        .andExpect(jsonPath("$.data.canManage").value(true))
        .andExpect(jsonPath("$.data.members[*].nickname", contains("방장", "참여자", "나중")))
        .andExpect(jsonPath("$.data.members[*].isOwner", contains(true, false, false)))
        .andExpect(
            jsonPath(
                "$.data.members[*].memberId",
                contains(
                    ownerMember.getId().intValue(),
                    participantMember.getId().intValue(),
                    late.getId().intValue())))
        .andExpect(jsonPath("$.data.members[0].userId").value(owner.getId()))
        .andExpect(jsonPath("$.data.members[0].profileImageUrl").isEmpty());
  }

  @Test
  @DisplayName("수신자 목록에는 수신자만 나온다")
  void listsRecipients() throws Exception {
    getMembers(owner, "RECIPIENT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("RECIPIENT"))
        .andExpect(jsonPath("$.data.count").value(1))
        .andExpect(jsonPath("$.data.members", hasSize(1)))
        .andExpect(jsonPath("$.data.members[0].nickname").value("수신자"))
        .andExpect(jsonPath("$.data.members[0].isOwner").value(false));
  }

  @Test
  @DisplayName("참여자 · 수신자도 목록을 볼 수 있고, canManage 는 false 다")
  void nonOwnersCanViewButNotManage() throws Exception {
    getMembers(participant, "PARTICIPANT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(2))
        .andExpect(jsonPath("$.data.canManage").value(false));
    getMembers(recipient, "RECIPIENT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canManage").value(false));
  }

  @Test
  @DisplayName("LOCKED 이거나, WRITING 이어도 마감이 지났으면 방장의 canManage 는 false 다")
  void ownerCannotManageAfterDeadline() throws Exception {
    TimeCapsule locked = saveCapsule(CapsuleStatus.LOCKED, FAR_FUTURE.minusDays(10));
    join(locked, owner, MemberRole.OWNER, 0);
    TimeCapsule pastDeadline =
        saveCapsule(CapsuleStatus.WRITING, LocalDateTime.now().minusMinutes(1));
    join(pastDeadline, owner, MemberRole.OWNER, 0);

    getMembers(owner, locked, "PARTICIPANT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canManage").value(false));
    getMembers(owner, pastDeadline, "PARTICIPANT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canManage").value(false));
  }

  @ParameterizedTest(name = "role={0}")
  @ValueSource(strings = {"OWNER", "ADMIN", ""})
  @DisplayName("role 이 OWNER 이거나 없는 값이거나 비었으면 400 이다")
  void rejectsInvalidRole(String role) throws Exception {
    getMembers(owner, role)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("role 을 보내지 않으면 400 이다")
  void rejectsMissingRole() throws Exception {
    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}/members", capsule.getId())
                .header(AUTHORIZATION, bearer(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("멤버가 아니면 404 이고, 삭제된 캡슐도 404 다")
  void listNotFound() throws Exception {
    getMembers(saveUser("member-stranger", "남"), "PARTICIPANT")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));

    capsule.delete(FAR_FUTURE);
    timeCapsuleRepository.save(capsule);
    getMembers(owner, "PARTICIPANT")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 목록을 볼 수 없다")
  void listRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}/members", capsule.getId())
                .param("role", "PARTICIPANT"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions getMembers(User user, String role) throws Exception {
    return getMembers(user, capsule, role);
  }

  private ResultActions getMembers(User user, TimeCapsule target, String role) throws Exception {
    return mockMvc.perform(
        get("/v1/time-capsules/{capsuleId}/members", target.getId())
            .param("role", role)
            .header(AUTHORIZATION, bearer(user)));
  }

  private String bearer(User user) {
    return "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDateTime letterDeadlineAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(letterDeadlineAt)
            .openAt(FAR_FUTURE)
            .build());
  }

  /** {@code joinedDaysAgo} 가 클수록 먼저 참여했다. */
  private CapsuleMember join(TimeCapsule target, User user, MemberRole role, int joinedDaysAgo) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(target)
            .user(user)
            .role(role)
            .status(MemberStatus.ACTIVE)
            .joinedAt(FAR_FUTURE.minusDays(100 + joinedDaysAgo))
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
