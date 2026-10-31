package com.momento.server.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteeRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.timecapsule.service.CapsuleInviteService;
import com.momento.server.domain.timecapsule.service.TimeCapsuleStatusService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 사건(캡슐 공개 · 초대 수락)이 실제로 커밋된 뒤 알림이 저장되는지 검증한다. 리스너가 AFTER_COMMIT 이라 테스트를 트랜잭션으로 감싸지 않고 서비스를 직접 부른다.
 */
@SpringBootTest
class NotificationEventIntegrationTest {

  @Autowired private TimeCapsuleStatusService statusService;
  @Autowired private CapsuleInviteService capsuleInviteService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private CapsuleInviteRepository capsuleInviteRepository;
  @Autowired private CapsuleInviteeRepository capsuleInviteeRepository;
  @Autowired private UserRepository userRepository;

  private User owner;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("event-owner", "상래");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("캡슐이 열리면 개봉 알림을 켠 ACTIVE 멤버에게 공개 알림이 간다")
  void capsuleOpenedNotifiesMembers() {
    LocalDateTime past = LocalDateTime.now().minusMinutes(1);
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            capsuleBuilder(CapsuleStatus.LOCKED)
                .letterDeadlineAt(past.minusDays(2))
                .openAt(past)
                .build());
    join(capsule, owner, MemberRole.OWNER);
    User recipient = join(capsule, saveUser("recipient", "유빈"), MemberRole.RECIPIENT);
    User off = saveUser("off", "꺼둠");
    off.getNotificationSettings().update(false, null, null, null, null);
    join(capsule, userRepository.save(off), MemberRole.PARTICIPANT);

    statusService.transition();

    List<Notification> notifications = notificationsOf(capsule);
    assertThat(notifications)
        .extracting(notification -> notification.getUser().getId())
        .containsExactlyInAnyOrder(owner.getId(), recipient.getId());
    assertThat(notifications)
        .allSatisfy(
            notification -> {
              assertThat(notification.getNotificationType())
                  .isEqualTo(NotificationType.CAPSULE_OPENED);
              assertThat(notification.getReferenceId()).isEqualTo(capsule.getId());
              assertThat(notification.getTitle()).isEqualTo("타임캡슐이 공개되었어요");
            });
  }

  @Test
  @DisplayName("초대를 수락하면 들어온 본인을 뺀 멤버에게 새 참여자 알림이 간다")
  void memberJoinedNotifiesOthers() {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            capsuleBuilder(CapsuleStatus.WRITING)
                .letterDeadlineAt(LocalDateTime.now().plusDays(5))
                .openAt(LocalDateTime.now().plusDays(10))
                .build());
    join(capsule, owner, MemberRole.OWNER);
    User participant = join(capsule, saveUser("participant", "유빈"), MemberRole.PARTICIPANT);
    User joiner = saveUser("joiner", "민건");
    String token =
        capsuleInviteService
            .issue(
                capsule.getId(),
                owner.getId(),
                new CapsuleInviteCreateRequest(MemberRole.PARTICIPANT))
            .getInviteToken();

    capsuleInviteService.accept(token, joiner.getId());

    List<Notification> notifications = notificationsOf(capsule);
    assertThat(notifications)
        .extracting(notification -> notification.getUser().getId())
        .containsExactlyInAnyOrder(owner.getId(), participant.getId());
    assertThat(notifications)
        .allSatisfy(
            notification -> {
              assertThat(notification.getNotificationType()).isEqualTo(NotificationType.NEW_MEMBER);
              assertThat(notification.getReferenceId()).isEqualTo(capsule.getId());
              assertThat(notification.getContent()).isEqualTo("민건님이 '캡슐' 캡슐에 참여했어요");
            });
  }

  /**
   * 이 테스트가 만든 캡슐의 알림만 본다. 상태 전이는 테스트 DB 의 모든 캡슐을 훑어, 다른 테스트 클래스가 남긴 캡슐이 있으면 그 멤버에게도 알림이 생길 수 있다.
   */
  private List<Notification> notificationsOf(TimeCapsule capsule) {
    return notificationRepository.findAll().stream()
        .filter(notification -> capsule.getId().equals(notification.getReferenceId()))
        .toList();
  }

  private TimeCapsule.TimeCapsuleBuilder capsuleBuilder(CapsuleStatus status) {
    return TimeCapsule.builder()
        .creator(owner)
        .iconType("ICE_CREAM")
        .title("캡슐")
        .capsuleType(CapsuleType.GROUP)
        .visibilityType(VisibilityType.ALL_MEMBERS)
        .status(status);
  }

  private User join(TimeCapsule capsule, User user, MemberRole role) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(MemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build());
    return user;
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
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
