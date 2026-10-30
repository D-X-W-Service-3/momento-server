package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.InviteeStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteeRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 캡슐 초대 API를 실제 필터·시큐리티까지 태워 검증한다.
 *
 * <p>{@link TimeCapsuleIntegrationTest} 와 같은 이유로 {@code @Transactional} 로 감싸지 않는다 — {@code
 * open-in-view: false} 에서 지연 로딩·잠금 경계가 테스트 트랜잭션에 가려지지 않게 하기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CapsuleInviteIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private CapsuleInviteRepository capsuleInviteRepository;
  @Autowired private CapsuleInviteeRepository capsuleInviteeRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private User owner;
  private String ownerToken;
  private TimeCapsule capsule;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("owner-kakao-id", "상래");
    ownerToken = tokenOf(owner);
    capsule = saveCapsule(CapsuleType.GROUP, CapsuleStatus.WRITING);
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  // ---------- 발급 ----------

  @Test
  @DisplayName("OWNER 가 발급하면 200 과 함께 초대 정보가 내려온다")
  void ownerIssuesInvite() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", capsule.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inviteId").isNumber())
        .andExpect(jsonPath("$.data.targetRole").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.inviteToken").isNotEmpty())
        .andExpect(jsonPath("$.data.expiresAt").value(formatted(capsule.getLetterDeadlineAt())));
  }

  @Test
  @DisplayName("활성 링크가 있으면 다시 발급해도 같은 토큰을 돌려준다")
  void issueReturnsExistingActiveLink() throws Exception {
    String first = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    String second = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    assertThat(second).isEqualTo(first);
    assertThat(capsuleInviteRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("targetRole 로 OWNER 를 보내면 400 이다")
  void ownerTargetRoleIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", capsule.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OWNER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVITE_ROLE"));
  }

  @Test
  @DisplayName("SELF 캡슐은 어떤 역할로도 초대할 수 없다")
  void selfCapsuleRejectsAnyInvite() throws Exception {
    TimeCapsule self = saveCapsule(CapsuleType.SELF, CapsuleStatus.WRITING);
    saveMember(self, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", self.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVITE_ROLE"));
  }

  @Test
  @DisplayName("FRIEND 캡슐은 RECIPIENT 로 초대할 수 없다")
  void friendCapsuleRejectsRecipientInvite() throws Exception {
    TimeCapsule friend = saveCapsule(CapsuleType.FRIEND, CapsuleStatus.WRITING);
    saveMember(friend, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", friend.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("RECIPIENT")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVITE_ROLE"));
  }

  @Test
  @DisplayName("FRIEND 캡슐은 PARTICIPANT 로는 초대할 수 있다")
  void friendCapsuleAllowsParticipantInvite() throws Exception {
    TimeCapsule friend = saveCapsule(CapsuleType.FRIEND, CapsuleStatus.WRITING);
    saveMember(friend, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", friend.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("OWNER 가 아닌 회원은 발급할 수 없다")
  void nonOwnerCannotIssue() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", capsule.getId())
                .header(AUTHORIZATION, bearer(tokenOf(participant)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));
  }

  @Test
  @DisplayName("WRITING 이 아닌 캡슐은 발급할 수 없다")
  void issueRejectedAfterWriting() throws Exception {
    TimeCapsule opened = saveCapsule(CapsuleType.GROUP, CapsuleStatus.OPENED);
    saveMember(opened, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", opened.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("편지 수정 마감이 지났으면 스케줄러가 잠그기 전(WRITING)이어도 발급할 수 없다")
  void issueRejectedAfterDeadlineBeforeLock() throws Exception {
    passDeadline(capsule);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", capsule.getId())
                .header(AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("인증 없이 발급할 수 없다")
  void issueRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites", capsule.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isUnauthorized());
  }

  // ---------- 재생성 ----------

  @Test
  @DisplayName("재생성하면 201 이고 기존 링크는 REVOKED, 새 링크는 다른 토큰이다")
  void regenerateRevokesPreviousInvite() throws Exception {
    String firstToken = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    String response =
        mockMvc
            .perform(
                post("/v1/time-capsules/{capsuleId}/invites/regenerate", capsule.getId())
                    .header(AUTHORIZATION, bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("PARTICIPANT")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondToken = JSON.readTree(response).path("data").path("inviteToken").asText();

    assertThat(secondToken).isNotEqualTo(firstToken);
    assertThat(capsuleInviteRepository.findByInviteToken(firstToken).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
    assertThat(capsuleInviteRepository.findByInviteToken(secondToken).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.ACTIVE);
  }

  @Test
  @DisplayName("거절한 링크로는 참여할 수 없고, 재생성된 새 링크로는 다시 참여할 수 있다")
  void rejecterCanJoinOnlyViaRegeneratedLink() throws Exception {
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    String firstToken = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    preview(firstToken, rejecter);
    reject(firstToken, rejecter).andExpect(status().isOk());
    accept(firstToken, rejecter).andExpect(status().isNotFound());

    String newToken = regenerate("PARTICIPANT");

    accept(newToken, rejecter).andExpect(status().isOk());
    assertThat(inviteeStatus(firstToken, rejecter)).isEqualTo(InviteeStatus.DECLINED);
    assertThat(inviteeStatus(newToken, rejecter)).isEqualTo(InviteeStatus.ACCEPTED);
  }

  @Test
  @DisplayName("OWNER 가 아닌 회원은 재생성할 수 없다")
  void nonOwnerCannotRegenerate() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);

    mockMvc
        .perform(
            post("/v1/time-capsules/{capsuleId}/invites/regenerate", capsule.getId())
                .header(AUTHORIZATION, bearer(tokenOf(participant)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PARTICIPANT")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));
  }

  // ---------- 미리보기 ----------

  @Test
  @DisplayName("링크를 열면 대기 중으로 기록되고, 캡슐 정보 · 참여자 미리보기가 함께 내려온다")
  void previewRecordsPendingAndReturnsCapsuleSummary() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User viewer = saveUser("viewer-kakao-id", "민건");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetRole").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.alreadyJoined").value(false))
        .andExpect(jsonPath("$.data.capsule.capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.capsule.iconType").value("ICE_CREAM"))
        .andExpect(jsonPath("$.data.capsule.title").value("캡슐"))
        .andExpect(jsonPath("$.data.capsule.capsuleType").value("GROUP"))
        .andExpect(jsonPath("$.data.capsule.status").value("WRITING"))
        .andExpect(jsonPath("$.data.capsule.memberCount").value(1))
        .andExpect(jsonPath("$.data.capsule.letterCount").value(0))
        .andExpect(jsonPath("$.data.capsule.participants.count").value(1))
        .andExpect(jsonPath("$.data.capsule.participants.previews[0].userId").value(owner.getId()))
        .andExpect(jsonPath("$.data.capsule.recipients.count").value(0));

    assertThat(inviteeStatus(token, viewer)).isEqualTo(InviteeStatus.PENDING);
  }

  @Test
  @DisplayName("링크 하나를 여러 명이 열 수 있고, 각자 대기 중으로 기록된다")
  void sharedLinkCanBeOpenedByManyMembers() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User first = saveUser("first-kakao-id", "민건");
    User second = saveUser("second-kakao-id", "서현");

    preview(token, first);
    preview(token, second);

    assertThat(inviteeStatus(token, first)).isEqualTo(InviteeStatus.PENDING);
    assertThat(inviteeStatus(token, second)).isEqualTo(InviteeStatus.PENDING);
    assertThat(capsuleInviteRepository.findByInviteToken(token).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.ACTIVE);
  }

  @Test
  @DisplayName("같은 회원이 다시 열어도 기록은 하나다")
  void previewIsIdempotentForSameUser() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User viewer = saveUser("viewer-kakao-id", "민건");

    preview(token, viewer);
    preview(token, viewer);

    assertThat(capsuleInviteeRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("이미 캡슐 멤버인 회원이 열면 alreadyJoined 가 true 이고 대기 중으로 기록하지 않는다")
  void previewReflectsAlreadyJoined() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token)
                .header(AUTHORIZATION, bearer(tokenOf(participant))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alreadyJoined").value(true));

    assertThat(capsuleInviteeRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("참여자 미리보기는 상세 화면과 같이 방장을 맨 앞에 두고 닉네임을 담는다")
  void previewListsOwnerFirstWithNicknames() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    User recipient = saveUser("recipient-kakao-id", "유빈");
    saveMember(capsule, recipient, MemberRole.RECIPIENT, MemberStatus.ACTIVE);
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");
    User viewer = saveUser("viewer-kakao-id", "서현");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsule.memberCount").value(3))
        .andExpect(jsonPath("$.data.capsule.participants.count").value(2))
        .andExpect(jsonPath("$.data.capsule.participants.previews[0].userId").value(owner.getId()))
        .andExpect(jsonPath("$.data.capsule.participants.previews[0].nickname").value("상래"))
        .andExpect(
            jsonPath("$.data.capsule.participants.previews[1].userId").value(participant.getId()))
        .andExpect(jsonPath("$.data.capsule.recipients.count").value(1))
        .andExpect(jsonPath("$.data.capsule.recipients.previews[0].nickname").value("유빈"));
  }

  @Test
  @DisplayName("재생성으로 무효가 된 링크, 만료된 링크, 삭제된 캡슐의 링크는 404 다")
  void previewRejectsUnusableLinks() throws Exception {
    User viewer = saveUser("viewer-kakao-id", "민건");
    String revoked = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    regenerate("PARTICIPANT");
    String expired = saveExpiredInvite(capsule, owner, MemberRole.PARTICIPANT).getInviteToken();
    TimeCapsule deleted = saveCapsule(CapsuleType.GROUP, CapsuleStatus.WRITING);
    saveMember(deleted, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    String ofDeleted = issue(ownerToken, deleted.getId(), "PARTICIPANT");
    deleteCapsule(deleted);

    for (String token : new String[] {revoked, expired, ofDeleted}) {
      mockMvc
          .perform(
              get("/v1/invites/{inviteToken}", token)
                  .header(AUTHORIZATION, bearer(tokenOf(viewer))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
    }
    assertThat(capsuleInviteeRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("존재하지 않는 토큰은 404 다")
  void previewUnknownTokenIsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", "no-such-token")
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 미리보기를 조회할 수 없다")
  void previewRequiresAuthentication() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc.perform(get("/v1/invites/{inviteToken}", token)).andExpect(status().isUnauthorized());
  }

  // ---------- 받은 초대 목록 조회 ----------

  @Test
  @DisplayName("연 링크가 받은 목록에 뜨고, 수락하면 빠진다")
  void receivedInvitesTracksOpenedThenClearsOnAccept() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");
    User invitee = saveUser("invitee-kakao-id", "민건");
    String inviteeToken = tokenOf(invitee);

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(inviteeToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(0));

    preview(token, invitee);

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(inviteeToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(1))
        .andExpect(jsonPath("$.data.invites[0].inviteToken").value(token))
        .andExpect(jsonPath("$.data.invites[0].targetRole").value("RECIPIENT"))
        .andExpect(jsonPath("$.data.invites[0].inviterNickname").value("상래"))
        .andExpect(jsonPath("$.data.invites[0].capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.invites[0].capsuleTitle").value("캡슐"))
        .andExpect(jsonPath("$.data.invites[0].openAt").value(formatted(capsule.getOpenAt())));

    accept(token, invitee).andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(inviteeToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(0));
  }

  @Test
  @DisplayName("거절한 초대는 받은 목록에서 빠진다")
  void receivedInvitesClearsOnReject() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User invitee = saveUser("invitee-kakao-id", "민건");
    preview(token, invitee);

    reject(token, invitee).andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(tokenOf(invitee))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(0));
  }

  @Test
  @DisplayName("인증 없이 받은 초대 목록을 조회할 수 없다")
  void receivedInvitesRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/v1/invites/received")).andExpect(status().isUnauthorized());
  }

  // ---------- 수락 ----------

  @Test
  @DisplayName("초대를 수락하면(미리보기 없이 바로 호출해도) 캡슐에 참여하고 내 기록은 ACCEPTED 다")
  void acceptJoinsCapsuleEvenWithoutPreviewFirst() throws Exception {
    User invitee = saveUser("invitee-kakao-id", "민건");
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    accept(token, invitee).andExpect(status().isOk());

    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(
                capsule.getId(), invitee.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    assertThat(member.getRole()).isEqualTo(MemberRole.PARTICIPANT);
    assertThat(inviteeStatus(token, invitee)).isEqualTo(InviteeStatus.ACCEPTED);
  }

  @Test
  @DisplayName("한 명이 수락해도 링크는 그대로라 다른 사람도 같은 링크로 참여한다")
  void acceptKeepsLinkForOthers() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User first = saveUser("first-kakao-id", "민건");
    User second = saveUser("second-kakao-id", "서현");
    preview(token, first);
    preview(token, second);

    accept(token, first).andExpect(status().isOk());
    accept(token, second).andExpect(status().isOk());

    assertThat(capsuleInviteRepository.findByInviteToken(token).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.ACTIVE);
    assertThat(
            capsuleMemberRepository.countByTimeCapsuleIdAndStatus(
                capsule.getId(), MemberStatus.ACTIVE))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("나갔던 회원이 새 초대를 수락하면 기존 행이 재활성화되고 역할이 갱신된다")
  void acceptReactivatesLeftMember() throws Exception {
    User rejoiner = saveUser("rejoiner-kakao-id", "동승환");
    saveMember(capsule, rejoiner, MemberRole.PARTICIPANT, MemberStatus.LEFT);
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");

    accept(token, rejoiner).andExpect(status().isOk());

    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserId(capsule.getId(), rejoiner.getId())
            .orElseThrow();
    assertThat(member.getRole()).isEqualTo(MemberRole.RECIPIENT);
    assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    assertThat(capsuleMemberRepository.findAll()).hasSize(2); // owner + rejoiner, 중복 생성 없음
  }

  @Test
  @DisplayName("이미 ACTIVE 참여자가 다시 수락하면 409 다")
  void acceptRejectsAlreadyActiveMember() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    accept(token, participant)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CAPSULE_MEMBER"));
  }

  @Test
  @DisplayName("거절한 링크로는 수락할 수 없다")
  void acceptRejectsDeclinedLink() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User invitee = saveUser("invitee-kakao-id", "민건");
    reject(token, invitee).andExpect(status().isOk());

    accept(token, invitee)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("재생성으로 무효가 된 링크를 수락하면 404 다")
  void acceptRejectsRevokedInvite() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    regenerate("PARTICIPANT");

    accept(token, saveUser("late-kakao-id", "늦참"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("만료된 링크를 수락하면 404 다")
  void acceptRejectsExpiredInvite() throws Exception {
    CapsuleInvite expired = saveExpiredInvite(capsule, owner, MemberRole.PARTICIPANT);

    accept(expired.getInviteToken(), saveUser("invitee-kakao-id", "민건"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("캡슐이 잠긴 이후에는 유효한 링크라도 수락할 수 없다")
  void acceptRejectsWhenCapsuleNotEditable() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User invitee = saveUser("invitee-kakao-id", "민건");
    preview(token, invitee);

    lockCapsule(capsule);

    accept(token, invitee)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("편지 수정 마감이 지났으면 스케줄러가 잠그기 전(WRITING)이어도 수락할 수 없다")
  void acceptRejectedAfterDeadlineBeforeLock() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    passDeadline(capsule);

    accept(token, saveUser("invitee-kakao-id", "민건"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("존재하지 않는 토큰을 수락하면 404 다")
  void acceptUnknownTokenIsNotFound() throws Exception {
    accept("no-such-token", owner)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 초대를 수락할 수 없다")
  void acceptRequiresAuthentication() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(post("/v1/invites/{inviteToken}/accept", token))
        .andExpect(status().isUnauthorized());
  }

  // ---------- 거절 ----------

  @Test
  @DisplayName("거절하면 내 기록만 DECLINED 가 되고 링크는 그대로다")
  void rejectDeclinesOnlyMyRecord() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    User other = saveUser("other-kakao-id", "서현");
    preview(token, rejecter);
    preview(token, other);

    reject(token, rejecter).andExpect(status().isOk());

    assertThat(inviteeStatus(token, rejecter)).isEqualTo(InviteeStatus.DECLINED);
    assertThat(inviteeStatus(token, other)).isEqualTo(InviteeStatus.PENDING);
    assertThat(capsuleInviteRepository.findByInviteToken(token).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.ACTIVE);
    accept(token, other).andExpect(status().isOk());
  }

  @Test
  @DisplayName("열지 않은 링크도 바로 거절할 수 있다")
  void rejectWithoutPreview() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User rejecter = saveUser("rejecter-kakao-id", "민건");

    reject(token, rejecter).andExpect(status().isOk());

    assertThat(inviteeStatus(token, rejecter)).isEqualTo(InviteeStatus.DECLINED);
  }

  @Test
  @DisplayName("거절한 링크를 다시 열면 404 다")
  void previewRejectsDeclinedLink() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    reject(token, rejecter).andExpect(status().isOk());

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("이미 거절한 초대를 다시 거절해도 에러 없이 성공한다")
  void rejectIsIdempotent() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User rejecter = saveUser("rejecter-kakao-id", "민건");

    reject(token, rejecter).andExpect(status().isOk());
    reject(token, rejecter).andExpect(status().isOk());

    assertThat(capsuleInviteeRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("이미 캡슐 멤버면 거절할 수 없다")
  void rejectRejectsAlreadyActiveMember() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    reject(token, participant)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CAPSULE_MEMBER"));
  }

  @Test
  @DisplayName("존재하지 않는 토큰을 거절하면 404 다")
  void rejectUnknownTokenIsNotFound() throws Exception {
    reject("no-such-token", owner)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 초대를 거절할 수 없다")
  void rejectRequiresAuthentication() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(post("/v1/invites/{inviteToken}/reject", token))
        .andExpect(status().isUnauthorized());
  }

  // ---------- 도우미 ----------

  private String issue(String token, Long capsuleId, String targetRole) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/v1/time-capsules/{capsuleId}/invites", capsuleId)
                    .header(AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(targetRole)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return JSON.readTree(response).path("data").path("inviteToken").asText();
  }

  private String regenerate(String targetRole) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/v1/time-capsules/{capsuleId}/invites/regenerate", capsule.getId())
                    .header(AUTHORIZATION, bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(targetRole)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return JSON.readTree(response).path("data").path("inviteToken").asText();
  }

  private void preview(String inviteToken, User user) throws Exception {
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", inviteToken)
                .header(AUTHORIZATION, bearer(tokenOf(user))))
        .andExpect(status().isOk());
  }

  private ResultActions accept(String inviteToken, User user) throws Exception {
    return mockMvc.perform(
        post("/v1/invites/{inviteToken}/accept", inviteToken)
            .header(AUTHORIZATION, bearer(tokenOf(user))));
  }

  private ResultActions reject(String inviteToken, User user) throws Exception {
    return mockMvc.perform(
        post("/v1/invites/{inviteToken}/reject", inviteToken)
            .header(AUTHORIZATION, bearer(tokenOf(user))));
  }

  private InviteeStatus inviteeStatus(String inviteToken, User user) {
    Long inviteId = capsuleInviteRepository.findByInviteToken(inviteToken).orElseThrow().getId();
    return capsuleInviteeRepository
        .findByCapsuleInviteIdAndUserId(inviteId, user.getId())
        .orElseThrow()
        .getStatus();
  }

  private static String body(String targetRole) {
    return "{\"targetRole\":" + (targetRole == null ? "null" : "\"" + targetRole + "\"") + "}";
  }

  private CapsuleInvite saveExpiredInvite(TimeCapsule capsule, User inviter, MemberRole role) {
    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(inviter)
            .inviteToken("expired-" + System.nanoTime())
            .targetRole(role)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build());
  }

  /**
   * 캡슐을 편지 마감 이후(LOCKED)로 직접 밀어 넣는다 — 상태 전이 스케줄러를 기다리지 않고 "잠긴 캡슐"을 테스트하기 위해서다. 관리되는 엔티티를 다시 읽어 필드만
   * 바꾸는 대신 JPQL UPDATE 를 쓴다 — {@code save()} 로 새로 만든 인스턴스를 병합하면 {@code createdAt} 처럼 세팅 안 한 필드가
   * null 로 덮어써질 위험이 있다.
   */
  private void lockCapsule(TimeCapsule capsule) {
    transactionTemplate.executeWithoutResult(
        tx ->
            entityManager
                .createQuery("update TimeCapsule c set c.status = :status where c.id = :id")
                .setParameter("status", CapsuleStatus.LOCKED)
                .setParameter("id", capsule.getId())
                .executeUpdate());
  }

  private void deleteCapsule(TimeCapsule capsule) {
    transactionTemplate.executeWithoutResult(
        tx ->
            entityManager
                .createQuery("update TimeCapsule c set c.deletedAt = :at where c.id = :id")
                .setParameter("at", LocalDateTime.now())
                .setParameter("id", capsule.getId())
                .executeUpdate());
  }

  /** 편지 수정 마감만 지나게 한다. 상태는 WRITING 그대로라, 스케줄러가 LOCKED 로 바꾸기 전 틈을 재현한다. 링크의 만료 시각은 건드리지 않는다. */
  private void passDeadline(TimeCapsule capsule) {
    transactionTemplate.executeWithoutResult(
        tx ->
            entityManager
                .createQuery("update TimeCapsule c set c.letterDeadlineAt = :at where c.id = :id")
                .setParameter("at", LocalDateTime.now().minusMinutes(1))
                .setParameter("id", capsule.getId())
                .executeUpdate());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(CapsuleType capsuleType, CapsuleStatus status) {
    LocalDateTime openAt = LocalDateTime.now().withNano(0).plusDays(30);
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(capsuleType)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .openAt(openAt)
            .letterDeadlineAt(openAt.minusDays(2))
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

  private String tokenOf(User user) {
    return tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String formatted(LocalDateTime dateTime) {
    return dateTime.withNano(0).toString();
  }

  private void cleanUp() {
    notificationRepository.deleteAllInBatch();
    capsuleInviteeRepository.deleteAllInBatch();
    capsuleInviteRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
