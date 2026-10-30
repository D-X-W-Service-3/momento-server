package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.letter.service.LetterService;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.timecapsule.service.CapsuleMemberService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import com.momento.server.global.common.exception.ApiException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
  @Autowired private LetterService letterService;
  @Autowired private CapsuleMemberService capsuleMemberService;

  private User owner;
  private User participant;
  private User recipient;
  private TimeCapsule capsule;
  private CapsuleMember ownerMember;
  private CapsuleMember participantMember;
  private CapsuleMember recipientMember;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("member-owner", "방장");
    participant = saveUser("member-participant", "참여자");
    recipient = saveUser("member-recipient", "수신자");
    capsule = saveCapsule(CapsuleStatus.WRITING, FAR_FUTURE.minusDays(10));
    participantMember = join(capsule, participant, MemberRole.PARTICIPANT, 3);
    ownerMember = join(capsule, owner, MemberRole.OWNER, 1);
    recipientMember = join(capsule, recipient, MemberRole.RECIPIENT, 2);
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

  // ---------- 내보내기 ----------

  @Test
  @DisplayName("내보내면 대상은 REMOVED 가 되고, 대상이 쓴 편지만 지워진다. 응답 데이터는 없다")
  void removesMemberAndTheirLetters() throws Exception {
    Letter targetLetter = saveLetter(participant, LetterStatus.DRAFT);
    Letter ownerLetter = saveLetter(owner, LetterStatus.SUBMITTED);

    remove(owner, participantMember)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());

    assertThat(reload(participantMember).getStatus()).isEqualTo(MemberStatus.REMOVED);
    assertThat(letterRepository.findById(targetLetter.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    assertThat(letterRepository.findById(ownerLetter.getId()).orElseThrow().getDeletedAt())
        .isNull();
    getMembers(owner, "PARTICIPANT").andExpect(jsonPath("$.data.count").value(1));
  }

  @Test
  @DisplayName("수신자도 내보낼 수 있다")
  void removesRecipient() throws Exception {
    remove(owner, recipientMember).andExpect(status().isOk());

    assertThat(reload(recipientMember).getStatus()).isEqualTo(MemberStatus.REMOVED);
  }

  @Test
  @DisplayName("내보낸 사람에게 그 캡슐은 상세 404 · 목록에서 사라짐 · 편지 작성 404 다")
  void removedMemberLosesAccess() throws Exception {
    remove(owner, participantMember).andExpect(status().isOk());

    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsule.getId())
                .header(AUTHORIZATION, bearer(participant)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/v1/time-capsules").header(AUTHORIZATION, bearer(participant)))
        .andExpect(jsonPath("$.data.capsules", hasSize(0)));
    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/letters", capsule.getId())
                .header(AUTHORIZATION, bearer(participant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"제목\", \"content\": \"편지\", \"themeType\": \"COZY\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("방장이 자기 자신을 내보내면 400 이다")
  void ownerCannotRemoveSelf() throws Exception {
    remove(owner, ownerMember)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWNER"));

    assertThat(reload(ownerMember).getStatus()).isEqualTo(MemberStatus.ACTIVE);
  }

  @Test
  @DisplayName("방장이 아니면 403 이고, 없는 멤버 ID 여도 404 가 아니라 403 이다")
  void nonOwnerGetsForbiddenBeforeTargetLookup() throws Exception {
    remove(participant, recipientMember)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));
    remove(participant, capsule, Long.MAX_VALUE)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));

    assertThat(reload(recipientMember).getStatus()).isEqualTo(MemberStatus.ACTIVE);
  }

  @Test
  @DisplayName("LOCKED 이거나, WRITING 이어도 마감이 지났으면 409 다")
  void cannotRemoveAfterDeadline() throws Exception {
    TimeCapsule locked = saveCapsule(CapsuleStatus.LOCKED, FAR_FUTURE.minusDays(10));
    join(locked, owner, MemberRole.OWNER, 0);
    CapsuleMember lockedTarget = join(locked, participant, MemberRole.PARTICIPANT, 0);
    TimeCapsule pastDeadline =
        saveCapsule(CapsuleStatus.WRITING, LocalDateTime.now().minusMinutes(1));
    join(pastDeadline, owner, MemberRole.OWNER, 0);
    CapsuleMember pastTarget = join(pastDeadline, participant, MemberRole.PARTICIPANT, 0);

    remove(owner, locked, lockedTarget.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
    remove(owner, pastDeadline, pastTarget.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));

    assertThat(reload(lockedTarget).getStatus()).isEqualTo(MemberStatus.ACTIVE);
    assertThat(reload(pastTarget).getStatus()).isEqualTo(MemberStatus.ACTIVE);
  }

  @Test
  @DisplayName("다른 캡슐의 멤버 · 이미 내보낸 멤버 · 없는 ID 는 404 CAPSULE_MEMBER_NOT_FOUND 다")
  void targetNotFound() throws Exception {
    TimeCapsule other = saveCapsule(CapsuleStatus.WRITING, FAR_FUTURE.minusDays(10));
    CapsuleMember elsewhere = join(other, saveUser("member-other", "남"), MemberRole.PARTICIPANT, 0);
    remove(owner, participantMember).andExpect(status().isOk());

    remove(owner, capsule, elsewhere.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_MEMBER_NOT_FOUND"));
    remove(owner, participantMember)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_MEMBER_NOT_FOUND"));
    remove(owner, capsule, Long.MAX_VALUE)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_MEMBER_NOT_FOUND"));

    assertThat(reload(elsewhere).getStatus()).isEqualTo(MemberStatus.ACTIVE);
  }

  @Test
  @DisplayName("내가 멤버가 아니면 404 CAPSULE_NOT_FOUND 다")
  void strangerGetsCapsuleNotFound() throws Exception {
    remove(saveUser("member-stranger", "남"), participantMember)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 내보낼 수 없다")
  void removeRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(
                "/v1/time-capsules/{capsuleId}/members/{memberId}",
                capsule.getId(),
                participantMember.getId()))
        .andExpect(status().isUnauthorized());
  }

  @RepeatedTest(5)
  @DisplayName("내보내기와 대상의 편지 작성이 동시에 들어와도 내보낸 사람의 편지가 남지 않는다")
  void removeAndWriteConcurrently() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> write =
          executor.submit(
              () -> {
                start.await();
                return letterService.create(
                    capsule.getId(),
                    participant.getId(),
                    new LetterCreateRequest("제목", "편지", "COZY"));
              });
      Future<?> remove =
          executor.submit(
              () -> {
                start.await();
                capsuleMemberService.remove(
                    capsule.getId(), owner.getId(), participantMember.getId());
                return null;
              });
      start.countDown();
      remove.get(10, TimeUnit.SECONDS);
      awaitWrite(write);

      assertThat(reload(participantMember).getStatus()).isEqualTo(MemberStatus.REMOVED);
      assertThat(
              letterRepository.existsByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                  capsule.getId(), participant.getId()))
          .isFalse();
    } finally {
      executor.shutdownNow();
    }
  }

  /** 편지 작성은 먼저 끝나면 성공하고(그 편지는 내보내기가 지운다), 늦으면 내보낸 뒤라 404 다. */
  private void awaitWrite(Future<?> write) throws Exception {
    try {
      write.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException exception) {
      ApiException cause = (ApiException) exception.getCause();
      assertThat(cause.getErrorCode()).isEqualTo(CapsuleErrorCode.CAPSULE_NOT_FOUND);
    }
  }

  private ResultActions remove(User user, CapsuleMember target) throws Exception {
    return remove(user, capsule, target.getId());
  }

  private ResultActions remove(User user, TimeCapsule target, Long memberId) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.delete(
                "/v1/time-capsules/{capsuleId}/members/{memberId}", target.getId(), memberId)
            .header(AUTHORIZATION, bearer(user)));
  }

  private CapsuleMember reload(CapsuleMember member) {
    return capsuleMemberRepository.findById(member.getId()).orElseThrow();
  }

  private Letter saveLetter(User author, LetterStatus letterStatus) {
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("편지")
            .status(letterStatus)
            .build());
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
