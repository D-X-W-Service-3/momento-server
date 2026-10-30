package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LetterDeleteQueryTest {
  @Autowired private LetterRepository letters;
  @Autowired private UserRepository users;
  @Autowired private TimeCapsuleRepository capsules;
  @Autowired private EntityManager em;
  private User author;
  private TimeCapsule capsule;
  private Letter letter;

  @BeforeEach
  void setUp() {
    author = users.save(User.builder().kakaoId("delete-query").nickname("삭제쿼리").build());
    capsule =
        capsules.save(
            TimeCapsule.builder()
                .creator(author)
                .title("캡슐")
                .iconType("ICE_CREAM")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .openAt(LocalDateTime.of(2045, 1, 1, 0, 0))
                .letterDeadlineAt(LocalDateTime.of(2044, 12, 1, 0, 0))
                .build());
    letter =
        letters.save(Letter.builder().author(author).timeCapsule(capsule).content("본문").build());
    em.flush();
    em.clear();
  }

  @Test
  void scalarLookupDoesNotManageLetterAndIsScopedToAuthor() {
    assertThat(letters.findCapsuleIdForDeletion(letter.getId(), author.getId()))
        .contains(capsule.getId());
    assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
    assertThat(letters.findCapsuleIdForDeletion(letter.getId(), Long.MAX_VALUE)).isEmpty();
    assertThat(letters.findCapsuleIdForDeletion(Long.MAX_VALUE, author.getId())).isEmpty();
  }

  @Test
  void lockedLookupIsScopedToExactLetterAuthorAndCapsule() {
    capsules.findActiveByIdForUpdate(capsule.getId()).orElseThrow();
    assertThat(letters.findActiveByIdForUpdate(letter.getId(), author.getId(), capsule.getId()))
        .isPresent();
    assertThat(letters.findActiveByIdForUpdate(letter.getId(), Long.MAX_VALUE, capsule.getId()))
        .isEmpty();
    assertThat(letters.findActiveByIdForUpdate(letter.getId(), author.getId(), Long.MAX_VALUE))
        .isEmpty();
    assertThat(letters.findActiveByIdForUpdate(Long.MAX_VALUE, author.getId(), capsule.getId()))
        .isEmpty();
  }

  @Test
  void deletedIdIsAbsentEvenWhenAnotherActiveLetterExists() {
    Letter old = letters.findById(letter.getId()).orElseThrow();
    old.delete(LocalDateTime.of(2027, 1, 1, 0, 0));
    Letter replacement =
        letters.save(Letter.builder().author(author).timeCapsule(capsule).content("새 편지").build());
    em.flush();
    em.clear();
    assertThat(letters.findCapsuleIdForDeletion(old.getId(), author.getId())).isEmpty();
    assertThat(letters.findActiveByIdForUpdate(old.getId(), author.getId(), capsule.getId()))
        .isEmpty();
    assertThat(
            letters.findActiveByIdForUpdate(replacement.getId(), author.getId(), capsule.getId()))
        .isPresent();
  }
}
