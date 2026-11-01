package com.momento.server.domain.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.home.repository.UserVisitRepository;
import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.repository.NotificationRepository;
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
 * 홈 조회 API 를 검증한다. 오늘은 2026-10-29(목요일) 로 고정한다.
 *
 * <p>상태 전이 스케줄러도 같은 시계를 쓰므로, 캡슐 상태는 오늘 기준으로 맞게 둔다(지난 개봉일은 OPENED, 지난 마감은 LOCKED).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HomeIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private UserVisitRepository userVisitRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private Clock clock;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    me = saveUser("home-me", "나");
    friend = saveUser("home-friend", "친구");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("홈 화면을 한 번에 준다")
  void returnsHome() throws Exception {
    TimeCapsule openedNotChecked =
        capsule(CapsuleStatus.OPENED, TODAY.minusDays(2).atStartOfDay(), "열린 캡슐");
    TimeCapsule locked = capsule(CapsuleStatus.LOCKED, TODAY.plusDays(3).atStartOfDay(), "잠긴 캡슐");
    TimeCapsule writing =
        capsule(CapsuleStatus.WRITING, TODAY.plusDays(10).atStartOfDay(), "작성 중 캡슐");
    capsule(CapsuleStatus.WRITING, TODAY.plusDays(20).atStartOfDay(), "네 번째");
    notification(me, false);

    home()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("나"))
        .andExpect(jsonPath("$.data.hasUnreadNotification").value(true))
        .andExpect(jsonPath("$.data.upcomingCapsule.capsuleId").value(locked.getId()))
        .andExpect(jsonPath("$.data.upcomingCapsule.title").value("잠긴 캡슐"))
        .andExpect(jsonPath("$.data.upcomingCapsule.openAt").value("2026-11-01T00:00:00"))
        .andExpect(jsonPath("$.data.upcomingCapsule.dDay").value(3))
        .andExpect(jsonPath("$.data.capsules.length()").value(3))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(openedNotChecked.getId()))
        .andExpect(jsonPath("$.data.capsules[0].status").value("OPENED"))
        .andExpect(jsonPath("$.data.capsules[0].openedByMe").value(false))
        .andExpect(jsonPath("$.data.capsules[0].dDay").value(-2))
        .andExpect(jsonPath("$.data.capsules[0].memberCount").value(1))
        .andExpect(jsonPath("$.data.capsules[1].capsuleId").value(locked.getId()))
        .andExpect(jsonPath("$.data.capsules[2].capsuleId").value(writing.getId()))
        .andExpect(jsonPath("$.data.weeklyVisit.days.length()").value(7))
        .andExpect(jsonPath("$.data.weeklyVisit.days[0].date").value("2026-10-26"))
        .andExpect(jsonPath("$.data.weeklyVisit.days[0].visited").value(false))
        .andExpect(jsonPath("$.data.weeklyVisit.days[3].date").value("2026-10-29"))
        .andExpect(jsonPath("$.data.weeklyVisit.days[3].visited").value(true))
        .andExpect(jsonPath("$.data.weeklyVisit.days[6].date").value("2026-11-01"))
        .andExpect(jsonPath("$.data.weeklyVisit.streak").value(1));
  }

  @Test
  @DisplayName("캡슐이 없으면 가장 먼저 열리는 캡슐은 null, 미리보기는 빈 목록이다")
  void withoutCapsules() throws Exception {
    notification(me, true);
    notification(friend, false);

    home()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasUnreadNotification").value(false))
        .andExpect(jsonPath("$.data.upcomingCapsule").value(nullValue()))
        .andExpect(jsonPath("$.data.capsules").value(empty()));
  }

  @Test
  @DisplayName("열린 캡슐은 가장 먼저 열리는 캡슐이 아니고, 내가 확인한 캡슐은 미리보기에서 빠진다")
  void openedCapsules() throws Exception {
    TimeCapsule checked = capsule(CapsuleStatus.OPENED, TODAY.minusDays(1).atStartOfDay(), "확인함");
    markOpened(checked);
    TimeCapsule notChecked =
        capsule(CapsuleStatus.OPENED, TODAY.minusDays(1).atStartOfDay(), "확인 안 함");

    home()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.upcomingCapsule").value(nullValue()))
        .andExpect(jsonPath("$.data.capsules.length()").value(1))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(notChecked.getId()));
  }

  @Test
  @DisplayName("연속 방문 일수는 지난주부터 이어서 센다")
  void streakAcrossWeeks() throws Exception {
    visit(TODAY.minusDays(5)); // 지난주 토요일
    visit(TODAY.minusDays(4)); // 지난주 일요일
    visit(TODAY.minusDays(3));
    visit(TODAY.minusDays(2));
    visit(TODAY.minusDays(1));

    home()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.weeklyVisit.days[0].visited").value(true))
        .andExpect(jsonPath("$.data.weeklyVisit.days[1].visited").value(true))
        .andExpect(jsonPath("$.data.weeklyVisit.days[2].visited").value(true))
        .andExpect(jsonPath("$.data.weeklyVisit.days[3].visited").value(true))
        .andExpect(jsonPath("$.data.weeklyVisit.days[4].visited").value(false))
        .andExpect(jsonPath("$.data.weeklyVisit.streak").value(6));
  }

  @Test
  @DisplayName("하루에 여러 번 조회해도 방문은 한 번이다")
  void visitsOncePerDay() throws Exception {
    home().andExpect(status().isOk());
    home().andExpect(status().isOk()).andExpect(jsonPath("$.data.weeklyVisit.streak").value(1));

    assertThat(userVisitRepository.findVisitDates(me.getId())).containsExactly(TODAY);
  }

  @Test
  @DisplayName("인증이 없으면 401 이고 방문도 남지 않는다")
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/v1/home")).andExpect(status().isUnauthorized());

    assertThat(userVisitRepository.findAll()).isEmpty();
  }

  private ResultActions home() throws Exception {
    return mockMvc.perform(
        get("/v1/home")
            .header(
                AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1))));
  }

  /** 편지 마감은 개봉 2일 전이다. WRITING 캡슐은 마감이 오늘 뒤가 되게 둔다. */
  private TimeCapsule capsule(CapsuleStatus status, LocalDateTime openAt, String title) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(me)
                .iconType("ICE_CREAM")
                .title(title)
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(status)
                .letterDeadlineAt(openAt.minusDays(2))
                .openAt(openAt)
                .build());
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(me)
            .role(MemberRole.OWNER)
            .joinedAt(openAt.minusDays(30))
            .build());
    return capsule;
  }

  private void markOpened(TimeCapsule capsule) {
    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(capsule.getId(), me.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), TODAY.atTime(1, 0)));
  }

  private void notification(User user, boolean read) {
    Notification notification =
        Notification.builder()
            .user(user)
            .notificationType(NotificationType.CAPSULE_OPENED)
            .title("타임캡슐이 열렸어요")
            .read(read)
            .build();
    notificationRepository.save(notification);
  }

  private void visit(LocalDate date) {
    transactionTemplate.executeWithoutResult(
        tx -> userVisitRepository.insertIfAbsent(me.getId(), date, date.atTime(9, 0)));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    userVisitRepository.deleteAllInBatch();
    notificationRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
