package com.momento.server.domain.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapsuleMemberTest {

  @Test
  @DisplayName("내보내면 행은 남고 상태만 REMOVED 가 된다")
  void remove() {
    CapsuleMember member = CapsuleMember.builder().role(MemberRole.PARTICIPANT).build();

    member.remove();

    assertThat(member.getStatus()).isEqualTo(MemberStatus.REMOVED);
    assertThat(member.getRole()).isEqualTo(MemberRole.PARTICIPANT);
  }
}
