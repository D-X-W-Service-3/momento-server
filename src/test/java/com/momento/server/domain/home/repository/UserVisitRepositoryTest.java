package com.momento.server.domain.home.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class UserVisitRepositoryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @Autowired private UserVisitRepository userVisitRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("visit-me", "나");
    other = saveUser("visit-other", "남");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("같은 날 두 번 넣어도 한 행만 남는다")
  void insertsOncePerDay() {
    assertThat(visit(me, TODAY, TODAY.atTime(9, 0))).isEqualTo(1);
    assertThat(visit(me, TODAY, TODAY.atTime(21, 0))).isZero();

    assertThat(userVisitRepository.findAll())
        .singleElement()
        .satisfies(
            visit -> {
              assertThat(visit.getVisitDate()).isEqualTo(TODAY);
              assertThat(visit.getCreatedAt()).isEqualTo(TODAY.atTime(9, 0));
            });
  }

  @Test
  @DisplayName("내가 들른 날만 읽는다")
  void findsMyVisitDates() {
    visit(me, TODAY.minusDays(1), TODAY.minusDays(1).atTime(9, 0));
    visit(me, TODAY, TODAY.atTime(9, 0));
    visit(other, TODAY.minusDays(2), TODAY.minusDays(2).atTime(9, 0));

    assertThat(userVisitRepository.findVisitDates(me.getId()))
        .containsExactlyInAnyOrder(TODAY.minusDays(1), TODAY);
  }

  private int visit(User user, LocalDate date, LocalDateTime now) {
    return transactionTemplate.execute(
        tx -> userVisitRepository.insertIfAbsent(user.getId(), date, now));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    userVisitRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
