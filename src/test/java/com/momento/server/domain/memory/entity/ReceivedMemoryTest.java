package com.momento.server.domain.memory.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReceivedMemoryTest {

  @Test
  @DisplayName("처음 받으면 받은 아카이브(PENDING)다")
  void pendingByDefault() {
    assertThat(ReceivedMemory.builder().build().isPending()).isTrue();
  }

  @Test
  @DisplayName("닫은 아카이브를 다시 열면 받은 아카이브로 돌아온다")
  void reopenDismissed() {
    ReceivedMemory received = ReceivedMemory.builder().build();
    received.dismiss();

    received.reopen();

    assertThat(received.getStatus()).isEqualTo(ReceivedMemoryStatus.PENDING);
  }

  @Test
  @DisplayName("저장한 아카이브는 다시 열어도 저장 상태 그대로다")
  void reopenSavedKeepsSaved() {
    ReceivedMemory received = ReceivedMemory.builder().build();
    received.save();

    received.reopen();

    assertThat(received.getStatus()).isEqualTo(ReceivedMemoryStatus.SAVED);
  }
}
