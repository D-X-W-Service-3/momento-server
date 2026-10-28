package com.momento.server.domain.timecapsule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상세 조회의 응답 필드 · 미리보기 · 권한을 시각을 고정해 검증한다.
 *
 * <p>고정 시각과 캡슐 일시를 모두 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 실제 오늘보다 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접
 * 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleDetailIntegrationTest {

  private static final Instant NOW = Instant.parse("2045-01-10T03:00:00Z");
  private static final LocalDateTime NOW_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final LocalDate TODAY = NOW_AT.toLocalDate();

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private Clock clock;

  private User owner;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    owner = saveUser("detail-owner", "방장");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("명세의 필드를 모두 준다")
  void returnsAllFields() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);

    detail(capsule, owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.iconType").value("ICE_CREAM"))
        .andExpect(jsonPath("$.data.title").value("캡슐"))
        .andExpect(jsonPath("$.data.description").value("설명"))
        .andExpect(jsonPath("$.data.capsuleType").value("GROUP"))
        .andExpect(jsonPath("$.data.visibilityType").value("ALL_MEMBERS"))
        .andExpect(jsonPath("$.data.status").value("WRITING"))
        .andExpect(jsonPath("$.data.openedByMe").value(false))
        .andExpect(jsonPath("$.data.letterDeadlineAt").value("2045-01-20T23:59:59"))
        .andExpect(jsonPath("$.data.openAt").value("2045-01-30T00:00:00"))
        .andExpect(jsonPath("$.data.dDay").value(20))
        .andExpect(jsonPath("$.data.memberCount").value(1))
        .andExpect(jsonPath("$.data.letterCount").value(0))
        .andExpect(jsonPath("$.data.myRole").value("OWNER"))
        .andExpect(jsonPath("$.data.participants.count").value(1))
        .andExpect(jsonPath("$.data.participants.previews[0].nickname").value("방장"))
        .andExpect(jsonPath("$.data.participants.previews[0].profileImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.recipients.count").value(0))
        .andExpect(jsonPath("$.data.recipients.previews", hasSize(0)));
  }

  @Test
  @DisplayName("확인한 열린 캡슐은 openedByMe 가 true 다")
  void openedByMe() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, VisibilityType.ALL_MEMBERS);
    CapsuleMember me = join(capsule, owner, MemberRole.OWNER, 0);
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(me.getId(), NOW_AT));

    detail(capsule, owner).andExpect(jsonPath("$.data.openedByMe").value(true));
  }

  @Test
  @DisplayName("미리보기는 방장을 맨 앞에 두고 참여 순으로 5명까지, 수신자는 따로 준다. 내보낸 멤버는 빠진다")
  void previews() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    for (int i = 1; i <= 6; i++) {
      join(capsule, saveUser("detail-p" + i, "참여" + i), MemberRole.PARTICIPANT, 10 - i);
    }
    join(capsule, owner, MemberRole.OWNER, 0);
    join(capsule, saveUser("detail-r", "수신"), MemberRole.RECIPIENT, 5);
    capsuleMemberRepository.save(
        member(
            capsule,
            saveUser("detail-x", "내보냄"),
            MemberRole.PARTICIPANT,
            20,
            MemberStatus.REMOVED));

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.memberCount").value(8))
        .andExpect(jsonPath("$.data.participants.count").value(7))
        .andExpect(
            jsonPath("$.data.participants.previews[*].nickname")
                .value(contains("방장", "참여1", "참여2", "참여3", "참여4")))
        .andExpect(jsonPath("$.data.recipients.count").value(1))
        .andExpect(jsonPath("$.data.recipients.previews[*].nickname").value(contains("수신")));
  }

  @Test
  @DisplayName("방장 · 작성 중 · 편지 0통이면 모든 관리 동작을 할 수 있다")
  void ownerPermissionsWithoutLetters() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(true))
        .andExpect(jsonPath("$.data.permissions.canInvite").value(true))
        .andExpect(jsonPath("$.data.permissions.canManage").value(true))
        .andExpect(jsonPath("$.data.permissions.canDelete").value(true))
        .andExpect(jsonPath("$.data.permissions.canViewLetters").value(false));
  }

  @Test
  @DisplayName("임시 저장 편지가 한 통이라도 있으면 삭제할 수 없고, 그 편지를 쓴 사람은 편지를 더 쓸 수 없다")
  void draftBlocksDeleteAndWriting() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);
    saveLetter(capsule, owner, LetterStatus.DRAFT, null);

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.letterCount").value(0))
        .andExpect(jsonPath("$.data.permissions.canDelete").value(false))
        .andExpect(jsonPath("$.data.permissions.canManage").value(true))
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(false));
  }

  @Test
  @DisplayName("삭제된 편지만 있으면 삭제할 수 있고, 그 편지를 쓴 사람은 다시 쓸 수 있다")
  void deletedLetterDoesNotCount() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);
    saveLetter(capsule, owner, LetterStatus.SUBMITTED, NOW_AT);

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.permissions.canDelete").value(true))
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(true));
  }

  @Test
  @DisplayName("참여자는 편지만 쓸 수 있고 초대 · 관리 · 삭제는 할 수 없다")
  void participantPermissions() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);
    User participant = saveUser("detail-participant", "참여자");
    join(capsule, participant, MemberRole.PARTICIPANT, 1);

    detail(capsule, participant)
        .andExpect(jsonPath("$.data.myRole").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(true))
        .andExpect(jsonPath("$.data.permissions.canInvite").value(false))
        .andExpect(jsonPath("$.data.permissions.canManage").value(false))
        .andExpect(jsonPath("$.data.permissions.canDelete").value(false));
  }

  @Test
  @DisplayName("수신자 + 작성자 공개 캡슐의 수신자는 편지를 쓸 수 없다")
  void recipientCannotWriteInRecipientAndAuthorCapsule() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.RECIPIENT_AND_AUTHOR);
    join(capsule, owner, MemberRole.OWNER, 0);
    User recipient = saveUser("detail-recipient", "수신자");
    join(capsule, recipient, MemberRole.RECIPIENT, 1);

    detail(capsule, recipient)
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(false));
  }

  @Test
  @DisplayName("잠긴 캡슐에서는 방장도 편지 작성 · 초대 · 관리 · 삭제를 할 수 없다")
  void lockedCapsulePermissions() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.LOCKED, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, 0);

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(false))
        .andExpect(jsonPath("$.data.permissions.canInvite").value(false))
        .andExpect(jsonPath("$.data.permissions.canManage").value(false))
        .andExpect(jsonPath("$.data.permissions.canDelete").value(false))
        .andExpect(jsonPath("$.data.permissions.canViewLetters").value(false));
  }

  @Test
  @DisplayName("마감이 지났으면 스케줄러가 LOCKED 로 바꾸기 전이라도 편지 작성 · 초대 · 관리 · 삭제를 할 수 없다")
  void pastDeadlineBeforeSchedulerRuns() throws Exception {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(CapsuleStatus.WRITING)
                .letterDeadlineAt(NOW_AT.minusSeconds(1))
                .openAt(TODAY.plusDays(20).atStartOfDay())
                .build());
    join(capsule, owner, MemberRole.OWNER, 0);

    detail(capsule, owner)
        .andExpect(jsonPath("$.data.status").value("WRITING"))
        .andExpect(jsonPath("$.data.permissions.canWriteLetter").value(false))
        .andExpect(jsonPath("$.data.permissions.canInvite").value(false))
        .andExpect(jsonPath("$.data.permissions.canManage").value(false))
        .andExpect(jsonPath("$.data.permissions.canDelete").value(false));
  }

  private ResultActions detail(TimeCapsule capsule, User user) throws Exception {
    return mockMvc.perform(
        get("/v1/time-capsules/{capsuleId}", capsule.getId())
            .header(
                AUTHORIZATION, "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1))));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, VisibilityType visibility) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .description("설명")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(visibility)
            .status(status)
            .letterDeadlineAt(TODAY.plusDays(10).atTime(23, 59, 59))
            .openAt(TODAY.plusDays(20).atStartOfDay())
            .build());
  }

  /** {@code joinedDaysAgo} 가 클수록 먼저 참여했다. */
  private CapsuleMember join(TimeCapsule capsule, User user, MemberRole role, int joinedDaysAgo) {
    return capsuleMemberRepository.save(
        member(capsule, user, role, joinedDaysAgo, MemberStatus.ACTIVE));
  }

  private CapsuleMember member(
      TimeCapsule capsule, User user, MemberRole role, int joinedDaysAgo, MemberStatus status) {
    return CapsuleMember.builder()
        .timeCapsule(capsule)
        .user(user)
        .role(role)
        .status(status)
        .joinedAt(NOW_AT.minusDays(joinedDaysAgo))
        .build();
  }

  private void saveLetter(
      TimeCapsule capsule, User author, LetterStatus status, LocalDateTime deletedAt) {
    letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("편지")
            .status(status)
            .deletedAt(deletedAt)
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
