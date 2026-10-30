package com.momento.server.domain.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapsuleInviteeTest {

  @Test
  @DisplayName("링크를 처음 열면 대기 중이다")
  void pendingByDefault() {
    CapsuleInvitee invitee = CapsuleInvitee.builder().build();

    assertThat(invitee.getStatus()).isEqualTo(InviteeStatus.PENDING);
    assertThat(invitee.isDeclined()).isFalse();
  }

  @Test
  @DisplayName("수락하면 ACCEPTED, 거절하면 DECLINED 가 된다")
  void acceptAndDecline() {
    CapsuleInvitee accepted = CapsuleInvitee.builder().build();
    CapsuleInvitee declined = CapsuleInvitee.builder().build();

    accepted.accept();
    declined.decline();

    assertThat(accepted.getStatus()).isEqualTo(InviteeStatus.ACCEPTED);
    assertThat(declined.getStatus()).isEqualTo(InviteeStatus.DECLINED);
    assertThat(declined.isDeclined()).isTrue();
  }
}
