package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 개봉 확인 API 를 시각을 고정해 검증한다.
 *
 * <p>캡슐의 공개 · 마감 일시는 실제 오늘과 고정 시각보다 모두 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌아, 지난 일시를 쓰면 다른 테스트 컨텍스트의
 * 스케줄러가 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleOpenIntegrationTest {

  private static final Instant NOW = Instant.parse("2030-01-01T09:12:30Z");
  private static final LocalDateTime NOW_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2040, 1, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;

  @MockitoBean private Clock clock;

  private User owner;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    owner = saveUser("open-owner", "상래");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("열린 캡슐의 멤버가 확인하면 200 과 함께 확인한 시각이 기록된다")
  void recordsOpenedAt() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    CapsuleMember me = saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    open(capsule, owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.openedAt").value("2030-01-01T09:12:30"));

    assertThat(openedAtOf(me)).isEqualTo(NOW_AT);
  }

  @Test
  @DisplayName("다시 확인해도 200 이고, 확인 시각은 처음 값 그대로다")
  void keepsFirstOpenedAt() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    CapsuleMember me = saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    open(capsule, owner).andExpect(status().isOk());

    given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(1)));

    open(capsule, owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.openedAt").value("2030-01-01T09:12:30"));
    assertThat(openedAtOf(me)).isEqualTo(NOW_AT);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = CapsuleStatus.class,
      names = {"WRITING", "LOCKED"})
  @DisplayName("공개 전 캡슐은 409 이고 아무것도 기록하지 않는다")
  void rejectsUnopenedCapsule(CapsuleStatus capsuleStatus) throws Exception {
    TimeCapsule capsule = saveCapsule(capsuleStatus, null);
    CapsuleMember me = saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    open(capsule, owner)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_OPENED"));

    assertThat(openedAtOf(me)).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(MemberRole.class)
  @DisplayName("역할과 관계없이 멤버면 확인할 수 있다")
  void everyRoleCanOpen(MemberRole role) throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    User member = role == MemberRole.OWNER ? owner : saveUser("open-member", "유빈");
    saveMember(capsule, member, role, MemberStatus.ACTIVE);

    open(capsule, member).andExpect(status().isOk());
  }

  @Test
  @DisplayName("한 멤버가 확인해도 다른 멤버의 확인 시각은 비어 있다")
  void doesNotAffectOtherMembers() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    CapsuleMember friend =
        saveMember(
            capsule, saveUser("open-friend", "유빈"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);

    open(capsule, owner).andExpect(status().isOk());

    assertThat(openedAtOf(friend)).isNull();
  }

  @Test
  @DisplayName("멤버가 아니면 열린 캡슐이어도 404 다")
  void rejectsNonMember() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    open(capsule, saveUser("open-stranger", "민수"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("멤버가 아니면 공개 전 캡슐이어도 409 가 아니라 404 다")
  void hidesStatusFromNonMember() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, null);
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    open(capsule, saveUser("open-stranger", "민수"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("내보내진 멤버는 404 다")
  void rejectsRemovedMember() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);
    User removed = saveUser("open-removed", "지수");
    CapsuleMember member =
        saveMember(capsule, removed, MemberRole.PARTICIPANT, MemberStatus.REMOVED);

    open(capsule, removed).andExpect(status().isNotFound());

    assertThat(openedAtOf(member)).isNull();
  }

  @Test
  @DisplayName("삭제된 캡슐은 404 다")
  void rejectsDeletedCapsule() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, NOW_AT.minusDays(1));
    saveMember(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);

    open(capsule, owner)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 확인할 수 없다")
  void requiresAuthentication() throws Exception {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, null);

    mockMvc
        .perform(post("/v1/time-capsules/{capsuleId}/open", capsule.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions open(TimeCapsule capsule, User user) throws Exception {
    return mockMvc.perform(
        post("/v1/time-capsules/{capsuleId}/open", capsule.getId())
            .header(
                AUTHORIZATION, "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1))));
  }

  private LocalDateTime openedAtOf(CapsuleMember member) {
    return capsuleMemberRepository.findById(member.getId()).orElseThrow().getOpenedAt();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(FAR_FUTURE.minusDays(10))
            .openAt(FAR_FUTURE)
            .deletedAt(deletedAt)
            .build());
  }

  private CapsuleMember saveMember(
      TimeCapsule capsule, User user, MemberRole role, MemberStatus status) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(NOW_AT)
            .build());
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
