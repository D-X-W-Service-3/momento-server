package com.momento.server.domain.anniversary.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnniversaryRepositoryTest {

  @Autowired private AnniversaryRepository anniversaryRepository;
  @Autowired private UserRepository userRepository;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("anniversary-me", "나");
    other = saveUser("anniversary-other", "남");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("내 기념일은 저장한 값 그대로 조회된다")
  void findsMine() {
    Anniversary saved =
        anniversaryRepository.save(
            Anniversary.create(
                me,
                "우리 100일",
                LocalDate.of(2026, 8, 12),
                RepeatType.YEARLY,
                "카페 가기",
                true,
                LocalTime.of(20, 30),
                3));

    Anniversary found =
        anniversaryRepository.findByIdAndUserId(saved.getId(), me.getId()).orElseThrow();

    assertThat(found.getTitle()).isEqualTo("우리 100일");
    assertThat(found.getAnniversaryDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(found.getRepeatType()).isEqualTo(RepeatType.YEARLY);
    assertThat(found.getMemo()).isEqualTo("카페 가기");
    assertThat(found.isNotificationEnabled()).isTrue();
    assertThat(found.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(found.getRemindDaysBefore()).isEqualTo(3);
  }

  @Test
  @DisplayName("다른 회원의 기념일과 없는 기념일은 조회되지 않는다")
  void hidesOthersAndMissing() {
    Anniversary others =
        anniversaryRepository.save(
            Anniversary.create(
                other,
                "남의 기념일",
                LocalDate.of(2026, 8, 12),
                RepeatType.NONE,
                null,
                false,
                null,
                null));

    assertThat(anniversaryRepository.findByIdAndUserId(others.getId(), me.getId())).isEmpty();
    assertThat(anniversaryRepository.findByIdAndUserId(others.getId() + 1, me.getId())).isEmpty();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    anniversaryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
