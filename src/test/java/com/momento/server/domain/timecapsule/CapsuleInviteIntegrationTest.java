package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
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
    capsule = saveCapsule(CapsuleType.GROUP, CapsuleStatus.WRITING, null);
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
        .andExpect(jsonPath("$.data.expiresAt").value(formatted(capsule.getOpenAt())));
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
    TimeCapsule self = saveCapsule(CapsuleType.SELF, CapsuleStatus.WRITING, null);
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
    TimeCapsule friend = saveCapsule(CapsuleType.FRIEND, CapsuleStatus.WRITING, null);
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
    TimeCapsule friend = saveCapsule(CapsuleType.FRIEND, CapsuleStatus.WRITING, null);
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
    TimeCapsule opened = saveCapsule(CapsuleType.GROUP, CapsuleStatus.OPENED, null);
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
  @DisplayName("거절했던 회원도 재생성된 새 링크로는 다시 참여할 수 있다")
  void rejecterCanJoinViaRegeneratedLink() throws Exception {
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    String firstToken = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", firstToken)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", firstToken)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());

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
    String newToken = JSON.readTree(response).path("data").path("inviteToken").asText();

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", newToken)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());
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

  // ---------- 취소 ----------

  @Test
  @DisplayName("OWNER 가 취소하면 200 이고 링크는 REVOKED 된다")
  void ownerCancelsInvite() throws Exception {
    Long inviteId = issueAndGetId(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(
            delete("/v1/time-capsules/{capsuleId}/invites/{inviteId}", capsule.getId(), inviteId)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());

    assertThat(capsuleInviteRepository.findById(inviteId).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
  }

  @Test
  @DisplayName("이미 취소된 링크를 다시 취소해도 에러 없이 성공한다")
  void cancelIsIdempotent() throws Exception {
    Long inviteId = issueAndGetId(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(
            delete("/v1/time-capsules/{capsuleId}/invites/{inviteId}", capsule.getId(), inviteId)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/v1/time-capsules/{capsuleId}/invites/{inviteId}", capsule.getId(), inviteId)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("존재하지 않는 초대 ID 를 취소하면 404 다")
  void cancelUnknownInviteIsNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/v1/time-capsules/{capsuleId}/invites/{inviteId}", capsule.getId(), 999_999L)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  // ---------- 미리보기 (선점) ----------

  @Test
  @DisplayName("처음 여는 회원에게 고정되고, 캡슐 정보·참여자 미리보기가 함께 내려온다")
  void previewClaimsLinkAndReturnsCapsuleSummary() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User viewer = saveUser("viewer-kakao-id", "민건");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetRole").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.alreadyJoined").value(false))
        .andExpect(jsonPath("$.data.capsule.capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.capsule.title").value("캡슐"))
        .andExpect(jsonPath("$.data.capsule.capsuleType").value("GROUP"))
        .andExpect(jsonPath("$.data.capsule.status").value("WRITING"))
        .andExpect(jsonPath("$.data.capsule.memberCount").value(1))
        .andExpect(jsonPath("$.data.capsule.letterCount").value(0))
        .andExpect(jsonPath("$.data.capsule.participants.count").value(0))
        .andExpect(jsonPath("$.data.capsule.recipients.count").value(0));

    assertThat(capsuleInviteRepository.findByInviteToken(token).orElseThrow().getInvitee().getId())
        .isEqualTo(viewer.getId());
  }

  @Test
  @DisplayName("이미 캡슐 멤버인 회원이 미리보면 alreadyJoined 가 true 다")
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
  }

  @Test
  @DisplayName("미리보기의 참여자 목록에 닉네임·프로필사진이 담긴다")
  void previewIncludesMemberNicknameAndProfileImage() throws Exception {
    User participant = saveUser("participant-kakao-id", "민건");
    saveMember(capsule, participant, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");
    User viewer = saveUser("viewer-kakao-id", "서현");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsule.participants.count").value(1))
        .andExpect(
            jsonPath("$.data.capsule.participants.previews[0].userId").value(participant.getId()))
        .andExpect(jsonPath("$.data.capsule.participants.previews[0].nickname").value("민건"));
  }

  @Test
  @DisplayName("이미 다른 회원이 선점한 링크를 다른 사람이 열면 404 다")
  void previewRejectsAfterClaimedByAnotherUser() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User first = saveUser("first-kakao-id", "민건");
    User second = saveUser("second-kakao-id", "서현");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(first))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(second))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("같은 회원이 다시 열어도 문제없다")
  void previewIsIdempotentForSameUser() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User viewer = saveUser("viewer-kakao-id", "민건");

    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(viewer))))
        .andExpect(status().isOk());
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
  @DisplayName("미리보기로 선점한 초대만 받은 목록에 뜨고, 수락하면 빠진다")
  void receivedInvitesTracksClaimedThenClearsOnAccept() throws Exception {
    User me = saveUser("me-kakao-id", "동승환");
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(tokenOf(me))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(0));

    mockMvc
        .perform(get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(me))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(tokenOf(me))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invites.length()").value(1))
        .andExpect(jsonPath("$.data.invites[0].inviteToken").value(token))
        .andExpect(jsonPath("$.data.invites[0].targetRole").value("PARTICIPANT"))
        .andExpect(jsonPath("$.data.invites[0].inviterNickname").value("상래"))
        .andExpect(jsonPath("$.data.invites[0].capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.invites[0].capsuleTitle").value("캡슐"));

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(me))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/invites/received").header(AUTHORIZATION, bearer(tokenOf(me))))
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
  @DisplayName("초대를 수락하면(미리보기 없이 바로 호출해도) 캡슐에 참여한다")
  void acceptInviteJoinsCapsuleEvenWithoutPreviewFirst() throws Exception {
    User invitee = saveUser("invitee-kakao-id", "민건");
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(invitee))))
        .andExpect(status().isOk());

    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(
                capsule.getId(), invitee.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    assertThat(member.getRole()).isEqualTo(MemberRole.PARTICIPANT);
  }

  @Test
  @DisplayName("나갔던 회원이 새 초대를 수락하면 기존 행이 재활성화되고 역할이 갱신된다")
  void acceptReactivatesLeftMember() throws Exception {
    User rejoiner = saveUser("rejoiner-kakao-id", "동승환");
    saveMember(capsule, rejoiner, MemberRole.PARTICIPANT, MemberStatus.LEFT);
    String token = issue(ownerToken, capsule.getId(), "RECIPIENT");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejoiner))))
        .andExpect(status().isOk());

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

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(participant))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CAPSULE_MEMBER"));
  }

  @Test
  @DisplayName("다른 회원이 이미 선점한 링크는 수락할 수 없다")
  void acceptRejectsAfterClaimedByAnotherUser() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User first = saveUser("first-kakao-id", "민건");
    User second = saveUser("second-kakao-id", "서현");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(first))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(second))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("취소된 링크를 수락하면 404 다")
  void acceptRejectsRevokedInvite() throws Exception {
    Long inviteId = issueAndGetId(ownerToken, capsule.getId(), "PARTICIPANT");
    String token = capsuleInviteRepository.findById(inviteId).orElseThrow().getInviteToken();
    mockMvc
        .perform(
            delete("/v1/time-capsules/{capsuleId}/invites/{inviteId}", capsule.getId(), inviteId)
                .header(AUTHORIZATION, bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(saveUser("late-kakao-id", "늦참")))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("만료된 링크를 수락하면 404 다")
  void acceptRejectsExpiredInvite() throws Exception {
    CapsuleInvite expired = saveExpiredInvite(capsule, owner, MemberRole.PARTICIPANT);
    User invitee = saveUser("invitee-kakao-id", "민건");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", expired.getInviteToken())
                .header(AUTHORIZATION, bearer(tokenOf(invitee))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("캡슐이 잠긴 이후에는 유효한 링크라도 수락할 수 없다")
  void acceptRejectsWhenCapsuleNotEditable() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User invitee = saveUser("invitee-kakao-id", "민건");
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(invitee))))
        .andExpect(status().isOk());

    lockCapsule(capsule);

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", token)
                .header(AUTHORIZATION, bearer(tokenOf(invitee))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("존재하지 않는 토큰을 수락하면 404 다")
  void acceptUnknownTokenIsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/accept", "no-such-token")
                .header(AUTHORIZATION, bearer(ownerToken)))
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
  @DisplayName("미리 선점한 회원이 거절하면 200 이고 링크는 REVOKED 된다")
  void rejectRevokesClaimedInvite() throws Exception {
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());

    assertThat(capsuleInviteRepository.findByInviteToken(token).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
  }

  @Test
  @DisplayName("한 번도 안 연 링크를 거절하면 그 회원에게 선점되면서 바로 거절 처리된다")
  void rejectClaimsUnopenedInviteThenRevokesIt() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User rejecter = saveUser("rejecter-kakao-id", "민건");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());

    CapsuleInvite invite = capsuleInviteRepository.findByInviteToken(token).orElseThrow();
    assertThat(invite.getStatus()).isEqualTo(InviteStatus.REVOKED);
    assertThat(invite.getInvitee().getId()).isEqualTo(rejecter.getId());
  }

  @Test
  @DisplayName("다른 회원이 이미 선점한 링크는 거절할 수 없다")
  void rejectRejectsAfterClaimedByAnotherUser() throws Exception {
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");
    User first = saveUser("first-kakao-id", "민건");
    User second = saveUser("second-kakao-id", "서현");
    mockMvc
        .perform(
            get("/v1/invites/{inviteToken}", token).header(AUTHORIZATION, bearer(tokenOf(first))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", token)
                .header(AUTHORIZATION, bearer(tokenOf(second))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
  }

  @Test
  @DisplayName("이미 거절한 초대를 다시 거절해도 에러 없이 성공한다")
  void rejectIsIdempotent() throws Exception {
    User rejecter = saveUser("rejecter-kakao-id", "민건");
    String token = issue(ownerToken, capsule.getId(), "PARTICIPANT");

    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", token)
                .header(AUTHORIZATION, bearer(tokenOf(rejecter))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("존재하지 않는 토큰을 거절하면 404 다")
  void rejectUnknownTokenIsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/v1/invites/{inviteToken}/reject", "no-such-token")
                .header(AUTHORIZATION, bearer(ownerToken)))
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

  private Long issueAndGetId(String token, Long capsuleId, String targetRole) throws Exception {
    String inviteToken = issue(token, capsuleId, targetRole);
    return capsuleInviteRepository.findByInviteToken(inviteToken).orElseThrow().getId();
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

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(
      CapsuleType capsuleType, CapsuleStatus status, LocalDateTime letterDeadlineAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .title("캡슐")
            .capsuleType(capsuleType)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .openAt(LocalDateTime.now().withNano(0).plusDays(30))
            .letterDeadlineAt(letterDeadlineAt)
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
    capsuleInviteRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
