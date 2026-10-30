package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LetterTest {
  @ParameterizedTest
  @EnumSource(LetterStatus.class)
  void softDeletionPreservesLetterAndFirstDeletionTime(LetterStatus status) {
    LocalDateTime now = LocalDateTime.of(2027, 1, 1, 12, 0);
    LocalDateTime submittedAt = status == LetterStatus.SUBMITTED ? now.minusDays(1) : null;
    Letter letter =
        Letter.builder()
            .content("본문")
            .themeType("COZY")
            .status(status)
            .submittedAt(submittedAt)
            .build();
    letter.delete(now);
    letter.delete(now.plusHours(1));
    assertThat(letter.getDeletedAt()).isEqualTo(now);
    assertThat(letter.getStatus()).isEqualTo(status);
    assertThat(letter.getSubmittedAt()).isEqualTo(submittedAt);
    assertThat(letter.getContent()).isEqualTo("본문");
    assertThat(letter.getThemeType()).isEqualTo("COZY");
  }
}
