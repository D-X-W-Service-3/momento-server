package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.letter.service.LetterService;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LetterDeleteServiceTest {
  @Mock private LetterRepository letters;
  @Mock private TimeCapsuleService capsules;
  private LetterService service;
  private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 12, 0);

  @BeforeEach
  void setUp() {
    service =
        new LetterService(
            letters, capsules, Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void missingOrForeignLetterDoesNotQueryCapsule() {
    when(letters.findCapsuleIdForDeletion(1L, 2L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(1L, 2L)).isInstanceOf(ApiException.class);
    verifyNoInteractions(capsules);
  }

  @Test
  void locksParentThenRechecksExactLetterBeforeDeleting() {
    TimeCapsule capsule =
        TimeCapsule.builder()
            .status(CapsuleStatus.WRITING)
            .letterDeadlineAt(NOW.plusHours(1))
            .openAt(NOW.plusDays(1))
            .build();
    Letter letter = Letter.builder().content("본문").build();
    when(letters.findCapsuleIdForDeletion(1L, 2L)).thenReturn(Optional.of(3L));
    when(capsules.getActiveCapsuleForUpdate(3L)).thenReturn(capsule);
    when(letters.findActiveByIdForUpdate(1L, 2L, 3L)).thenReturn(Optional.of(letter));
    service.delete(1L, 2L);
    var order = inOrder(letters, capsules);
    order.verify(letters).findCapsuleIdForDeletion(1L, 2L);
    order.verify(capsules).getActiveCapsuleForUpdate(3L);
    order.verify(capsules).requireActiveMember(3L, 2L);
    order.verify(letters).findActiveByIdForUpdate(1L, 2L, 3L);
    assertThat(letter.getDeletedAt()).isEqualTo(NOW);
  }

  @Test
  void letterDeletedDuringWaitIsNotReplacedByAnotherActiveLetter() {
    when(letters.findCapsuleIdForDeletion(1L, 2L)).thenReturn(Optional.of(3L));
    when(letters.findActiveByIdForUpdate(1L, 2L, 3L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(1L, 2L)).isInstanceOf(ApiException.class);
  }
}
