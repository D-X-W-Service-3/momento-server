package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.timecapsule.service.TimeCapsuleOpenedEvent;
import com.momento.server.domain.timecapsule.service.TimeCapsuleStatusService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 상태 전이 규칙을 검증한다. 스케줄러가 실제로 도는 것을 기다리면 느리고 불안정해서, 주기를 길게 밀어 두고 서비스를 직접 부른다.
 *
 * <p>{@link Clock} 을 고정해 공개 시각 정각 같은 경계를 확인한다.
 */
@SpringBootTest
@RecordApplicationEvents
@TestPropertySource(properties = "momento.capsule.status-transition-interval=1h")
class TimeCapsuleStatusTransitionTest {

  private static final Instant NOW = Instant.parse("2026-12-25T20:00:00Z");
  private static final LocalDateTime TODAY = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

  @Autowired private TimeCapsuleStatusService statusService;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApplicationEvents events;

  @MockitoBean private Clock clock;

  private User owner;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    owner = userRepository.save(User.builder().kakaoId("status-owner").nickname("상래").build());
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    timeCapsuleRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("공개 시각이 지난 캡슐은 OPENED 가 된다")
  void opensCapsulesPastOpenAt() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY.minusMinutes(1), null, null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.OPENED);
  }

  @Test
  @DisplayName("공개 시각 정각에 열린다")
  void opensAtExactOpenAt() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY, null, null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.OPENED);
  }

  @Test
  @DisplayName("공개 시각이 아직 안 됐으면 그대로 둔다")
  void keepsCapsulesBeforeOpenAt() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY.plusSeconds(1), null, null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.WRITING);
  }

  @Test
  @DisplayName("편지 마감은 지났고 공개 시각은 안 됐으면 LOCKED 가 된다")
  void locksCapsulesPastDeadline() {
    TimeCapsule capsule =
        save(CapsuleStatus.WRITING, TODAY.plusDays(1), TODAY.minusMinutes(1), null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.LOCKED);
  }

  @Test
  @DisplayName("편지 마감이 없으면 LOCKED 를 거치지 않고 공개 시각에 바로 열린다")
  void skipsLockedWhenDeadlineIsNull() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY, null, null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.OPENED);
  }

  @Test
  @DisplayName("마감과 공개 시각이 모두 지났으면 한 번에 OPENED 가 된다")
  void opensDirectlyWhenBothPassed() {
    TimeCapsule capsule =
        save(CapsuleStatus.WRITING, TODAY.minusHours(1), TODAY.minusHours(2), null);

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.OPENED);
  }

  @Test
  @DisplayName("삭제된 캡슐은 전이 대상이 아니다")
  void ignoresDeletedCapsules() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY.minusDays(1), null, TODAY.minusDays(2));

    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.WRITING);
    assertThat(openedEvents()).isEmpty();
  }

  @Test
  @DisplayName("여러 번 돌려도 결과가 같고, 이미 열린 캡슐로는 알림 이벤트가 다시 나가지 않는다")
  void isIdempotent() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY.minusDays(1), null, null);

    statusService.transition();
    statusService.transition();
    statusService.transition();

    assertThat(statusOf(capsule)).isEqualTo(CapsuleStatus.OPENED);
    assertThat(openedEvents()).hasSize(1);
  }

  @Test
  @DisplayName("열린 캡슐의 ID 가 알림이 붙을 이벤트에 담긴다")
  void publishesOpenedCapsuleIds() {
    TimeCapsule opened = save(CapsuleStatus.WRITING, TODAY.minusDays(1), null, null);
    save(CapsuleStatus.WRITING, TODAY.plusDays(1), null, null);

    statusService.transition();

    List<TimeCapsuleOpenedEvent> published = openedEvents();
    assertThat(published).hasSize(1);
    assertThat(published.get(0).capsuleIds()).containsExactly(opened.getId());
    assertThat(published.get(0).openedAt()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("열리고 나면 편지 열람 판정이 통과한다 — 전이 전에는 항상 막혀 있다")
  void openedCapsuleAllowsLetterViewing() {
    TimeCapsule capsule = save(CapsuleStatus.WRITING, TODAY.minusDays(1), null, null);
    assertThat(reload(capsule).canViewLetters(MemberRole.PARTICIPANT)).isFalse();

    statusService.transition();

    assertThat(reload(capsule).canViewLetters(MemberRole.PARTICIPANT)).isTrue();
  }

  private List<TimeCapsuleOpenedEvent> openedEvents() {
    return events.stream(TimeCapsuleOpenedEvent.class).toList();
  }

  private CapsuleStatus statusOf(TimeCapsule capsule) {
    return reload(capsule).getStatus();
  }

  private TimeCapsule reload(TimeCapsule capsule) {
    return timeCapsuleRepository.findById(capsule.getId()).orElseThrow();
  }

  private TimeCapsule save(
      CapsuleStatus status, LocalDateTime openAt, LocalDateTime deadline, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .openAt(openAt)
            .letterDeadlineAt(deadline)
            .deletedAt(deletedAt)
            .build());
  }
}
