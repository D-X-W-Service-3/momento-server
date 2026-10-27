package com.momento.server.domain.timecapsule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
 * 탭 홈 조회 API 를 시각을 고정해 검증한다.
 *
 * <p>고정 시각과 캡슐 일시를 모두 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 실제 오늘보다 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접
 * 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleOverviewIntegrationTest {

  private static final Instant NOW = Instant.parse("2045-01-10T03:00:00Z");
  private static final LocalDateTime NOW_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final LocalDate TODAY = NOW_AT.toLocalDate();

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private CapsuleInviteRepository capsuleInviteRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private Clock clock;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    me = saveUser("overview-me", "상래");
    friend = saveUser("overview-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("섹션마다 정해진 캡슐이 정해진 순서로 담긴다")
  void fillsSectionsInOrder() throws Exception {
    TimeCapsule writingLateDeadline = mine("작성 늦은 마감", CapsuleStatus.WRITING, 8, 10);
    TimeCapsule writingEarlyDeadline = mine("작성 이른 마감", CapsuleStatus.WRITING, 2, 30);
    TimeCapsule locked = mine("잠김", CapsuleStatus.LOCKED, -1, 5);
    TimeCapsule openedOld = mine("오래전 열림", CapsuleStatus.OPENED, -40, -30);
    TimeCapsule openedRecent = mine("최근 열림", CapsuleStatus.OPENED, -10, -1);
    markOpened(openedOld);
    markOpened(openedRecent);

    overview()
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.upcoming[*].capsuleId")
                .value(contains(id(locked), id(writingLateDeadline), id(writingEarlyDeadline))))
        .andExpect(
            jsonPath("$.data.writing[*].capsuleId")
                .value(contains(id(writingEarlyDeadline), id(writingLateDeadline))))
        .andExpect(jsonPath("$.data.locked[*].capsuleId").value(contains(id(locked))))
        .andExpect(
            jsonPath("$.data.opened[*].capsuleId")
                .value(contains(id(openedRecent), id(openedOld))));
  }

  @Test
  @DisplayName("열렸지만 확인하지 않은 캡슐은 곧 만날 캡슐 맨 앞에 개봉 가능 상태로 나온다")
  void unopenedOpenedCapsuleComesFirst() throws Exception {
    TimeCapsule opened = mine("열림", CapsuleStatus.OPENED, -9, 0);
    mine("작성 중", CapsuleStatus.WRITING, 2, 10);

    overview()
        .andExpect(jsonPath("$.data.upcoming[0].capsuleId").value(id(opened)))
        .andExpect(jsonPath("$.data.upcoming[0].status").value("OPENED"))
        .andExpect(jsonPath("$.data.upcoming[0].openedByMe").value(false))
        .andExpect(jsonPath("$.data.upcoming[0].dDay").value(0))
        .andExpect(jsonPath("$.data.opened[0].capsuleId").value(id(opened)));
  }

  @Test
  @DisplayName("두 섹션에 함께 나온 캡슐도 카드 내용은 같다")
  void sameCardAcrossSections() throws Exception {
    TimeCapsule capsule = mine("작성 중", CapsuleStatus.WRITING, 3, 10);
    join(capsule, friend);

    overview()
        .andExpect(jsonPath("$.data.upcoming[0].memberCount").value(2))
        .andExpect(jsonPath("$.data.writing[0].memberCount").value(2))
        .andExpect(jsonPath("$.data.upcoming[0].deadlineDDay").value(3))
        .andExpect(jsonPath("$.data.writing[0].deadlineDDay").value(3));
  }

  @Test
  @DisplayName("섹션은 최대 다섯 개다")
  void limitsEachSectionToFive() throws Exception {
    for (int i = 0; i < 6; i++) {
      mine("작성 중 " + i, CapsuleStatus.WRITING, 2 + i, 20);
    }

    overview()
        .andExpect(jsonPath("$.data.upcoming", hasSize(5)))
        .andExpect(jsonPath("$.data.writing", hasSize(5)));
  }

  @Test
  @DisplayName("남의 캡슐과 내보내진 캡슐은 어느 섹션에도 나오지 않는다")
  void excludesOthersAndRemoved() throws Exception {
    TimeCapsule others = saveCapsule("남의 캡슐", CapsuleStatus.WRITING, 2, 10);
    join(others, friend);
    TimeCapsule removed = saveCapsule("내보내진 캡슐", CapsuleStatus.WRITING, 2, 10);
    capsuleMemberRepository.save(member(removed, me, MemberStatus.REMOVED));

    overview()
        .andExpect(jsonPath("$.data.upcoming", hasSize(0)))
        .andExpect(jsonPath("$.data.writing", hasSize(0)));
  }

  @Test
  @DisplayName("내가 링크를 열고 응답하지 않은 초대가 있으면 배너를 띄운다")
  void showsPendingInviteBanner() throws Exception {
    TimeCapsule others = saveCapsule("남의 캡슐", CapsuleStatus.WRITING, 2, 10);
    join(others, friend);
    saveInvite(others, NOW_AT.plusDays(1));

    overview().andExpect(jsonPath("$.data.hasPendingInvite").value(true));
  }

  @Test
  @DisplayName("초대가 고정 시각 기준으로 만료됐으면 배너를 띄우지 않는다")
  void hidesBannerForExpiredInvite() throws Exception {
    TimeCapsule others = saveCapsule("남의 캡슐", CapsuleStatus.WRITING, 2, 10);
    join(others, friend);
    saveInvite(others, NOW_AT);

    overview().andExpect(jsonPath("$.data.hasPendingInvite").value(false));
  }

  @Test
  @DisplayName("캡슐도 초대도 없으면 빈 섹션과 false 를 준다")
  void emptyOverview() throws Exception {
    overview()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasPendingInvite").value(false))
        .andExpect(jsonPath("$.data.upcoming", hasSize(0)))
        .andExpect(jsonPath("$.data.writing", hasSize(0)))
        .andExpect(jsonPath("$.data.locked", hasSize(0)))
        .andExpect(jsonPath("$.data.opened", hasSize(0)));
  }

  @Test
  @DisplayName("인증 없이 조회할 수 없다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/time-capsules/overview"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions overview() throws Exception {
    return mockMvc.perform(
        get("/v1/time-capsules/overview")
            .header(
                AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1))));
  }

  private int id(TimeCapsule capsule) {
    return capsule.getId().intValue();
  }

  /** 마감 · 개봉일은 오늘부터 며칠 뒤인지로 받는다. 음수면 지난 날이다. */
  private TimeCapsule mine(String title, CapsuleStatus status, int deadlineDays, int openDays) {
    TimeCapsule capsule = saveCapsule(title, status, deadlineDays, openDays);
    join(capsule, me);
    return capsule;
  }

  private TimeCapsule saveCapsule(
      String title, CapsuleStatus status, int deadlineDays, int openDays) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title(title)
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(TODAY.plusDays(deadlineDays).atTime(23, 59, 59))
            .openAt(TODAY.plusDays(openDays).atStartOfDay())
            .build());
  }

  private void join(TimeCapsule capsule, User user) {
    capsuleMemberRepository.save(member(capsule, user, MemberStatus.ACTIVE));
  }

  private CapsuleMember member(TimeCapsule capsule, User user, MemberStatus status) {
    return CapsuleMember.builder()
        .timeCapsule(capsule)
        .user(user)
        .role(user == me ? MemberRole.OWNER : MemberRole.PARTICIPANT)
        .status(status)
        .joinedAt(NOW_AT)
        .build();
  }

  private void markOpened(TimeCapsule capsule) {
    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(capsule.getId(), me.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), NOW_AT));
  }

  private void saveInvite(TimeCapsule capsule, LocalDateTime expiresAt) {
    capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(friend)
            .invitee(me)
            .inviteToken(UUID.randomUUID().toString())
            .status(InviteStatus.ACTIVE)
            .expiresAt(expiresAt)
            .build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    capsuleInviteRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
