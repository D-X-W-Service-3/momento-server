package com.momento.server.domain.memory.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryTest {

  private Memory memory() {
    return Memory.builder()
        .title("제주도 여행")
        .content("바다가 정말 예뻤다")
        .memoryDate(LocalDate.of(2026, 8, 15))
        .build();
  }

  @Test
  @DisplayName("보낸 값만 바꾸고 null 인 값은 그대로 둔다")
  void updateOnlyGivenValues() {
    Memory memory = memory();

    memory.update("부산 여행", null, null);

    assertThat(memory.getTitle()).isEqualTo("부산 여행");
    assertThat(memory.getContent()).isEqualTo("바다가 정말 예뻤다");
    assertThat(memory.getMemoryDate()).isEqualTo(LocalDate.of(2026, 8, 15));
  }

  @Test
  @DisplayName("모든 값을 보내면 모두 바꾼다")
  void updateAllValues() {
    Memory memory = memory();

    memory.update("부산 여행", "갈매기가 많았다", LocalDate.of(2025, 5, 1));

    assertThat(memory.getTitle()).isEqualTo("부산 여행");
    assertThat(memory.getContent()).isEqualTo("갈매기가 많았다");
    assertThat(memory.getMemoryDate()).isEqualTo(LocalDate.of(2025, 5, 1));
  }

  @Test
  @DisplayName("삭제하면 삭제 시각만 채우고 내용은 남긴다")
  void delete() {
    Memory memory = memory();
    LocalDateTime now = LocalDateTime.of(2026, 10, 30, 12, 0);

    memory.delete(now);

    assertThat(memory.getDeletedAt()).isEqualTo(now);
    assertThat(memory.getTitle()).isEqualTo("제주도 여행");
  }
}
