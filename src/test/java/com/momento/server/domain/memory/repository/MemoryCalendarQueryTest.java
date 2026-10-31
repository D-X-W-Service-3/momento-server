package com.momento.server.domain.memory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.util.LikePattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 캘린더 · 통합 검색이 읽는 추억 쿼리를 검증한다. 아카이브 목록과 같은 추억(내가 직접 등록하고 삭제되지 않은 것)만 보는지가 핵심이다. */
@SpringBootTest
class MemoryCalendarQueryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

  @Autowired private MemoryRepository memoryRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private UserRepository userRepository;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("memory-calendar-me", "나");
    other = saveUser("memory-calendar-other", "남");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("날짜 범위 조회는 시작 날짜를 넣고 끝 날짜를 뺀다")
  void datesBetweenIncludesFromExcludesTo() {
    LocalDate to = DAY.plusMonths(1);
    save(me, DAY.minusDays(1));
    save(me, DAY);
    save(me, to.minusDays(1));
    save(me, to);

    assertThat(memoryRepository.findMyMemoryDatesBetween(me.getId(), DAY, to))
        .containsExactlyInAnyOrder(DAY, to.minusDays(1));
  }

  @Test
  @DisplayName("삭제된 추억 · 캡슐에서 옮겨 온 추억 · 남의 추억은 날짜 · 날짜별 목록 · 개수에서 모두 빠진다")
  void excludesDeletedMovedAndOthers() {
    Memory mine = save(me, DAY);
    memoryRepository.save(
        Memory.builder()
            .user(me)
            .title("삭제")
            .memoryDate(DAY)
            .deletedAt(DAY.atStartOfDay())
            .build());
    memoryRepository.save(
        Memory.builder().user(me).timeCapsule(saveCapsule()).title("옮김").memoryDate(DAY).build());
    save(other, DAY);

    assertThat(memoryRepository.findMyMemoryDatesBetween(me.getId(), DAY, DAY.plusDays(1)))
        .containsExactly(DAY);
    assertThat(memoryRepository.findMyMemoriesOn(me.getId(), DAY))
        .extracting(Memory::getId)
        .containsExactly(mine.getId());
    assertThat(memoryRepository.countMyMemories(me.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("날짜별 목록은 그 날 추억만 등록 순으로 나온다")
  void memoriesOnInIdOrder() {
    Memory first = save(me, DAY);
    Memory second = save(me, DAY);
    save(me, DAY.plusDays(1));

    assertThat(memoryRepository.findMyMemoriesOn(me.getId(), DAY))
        .extracting(Memory::getId)
        .containsExactly(first.getId(), second.getId());
  }

  @Test
  @DisplayName("검색은 제목에 검색어가 든 추억을 날짜 · ID 오름차순으로 찾고, % 는 글자 그대로 찾는다")
  void searchesByTitleInAscendingOrder() {
    Memory later = save(me, "제주 다시", DAY.plusDays(1));
    Memory first = save(me, "제주 여행", DAY);
    Memory second = save(me, "또 제주", DAY);
    save(me, "서울", DAY);
    save(other, "남의 제주", DAY);
    memoryRepository.save(
        Memory.builder()
            .user(me)
            .title("삭제된 제주")
            .memoryDate(DAY)
            .deletedAt(DAY.atStartOfDay())
            .build());
    memoryRepository.save(
        Memory.builder()
            .user(me)
            .timeCapsule(saveCapsule())
            .title("옮긴 제주")
            .memoryDate(DAY)
            .build());
    Memory percent = save(me, "100% 행복", DAY);

    assertThat(search("제주", null, null))
        .extracting(Memory::getId)
        .containsExactly(first.getId(), second.getId(), later.getId());
    assertThat(search("%", null, null)).extracting(Memory::getId).containsExactly(percent.getId());
  }

  @Test
  @DisplayName("검색의 날짜 범위는 시작을 넣고 끝을 빼며, 비어 있으면 전체 기간이다")
  void searchFiltersByDateRange() {
    LocalDate to = DAY.plusYears(1);
    save(me, "추억 전", DAY.minusDays(1));
    Memory first = save(me, "추억 시작", DAY);
    Memory last = save(me, "추억 끝 전날", to.minusDays(1));
    save(me, "추억 끝", to);

    assertThat(search("추억", DAY, to))
        .extracting(Memory::getId)
        .containsExactly(first.getId(), last.getId());
    assertThat(search("추억", null, null)).hasSize(4);
  }

  private List<Memory> search(String keyword, LocalDate from, LocalDate to) {
    return memoryRepository.searchMyMemories(me.getId(), LikePattern.contains(keyword), from, to);
  }

  private Memory save(User user, String title, LocalDate date) {
    return memoryRepository.save(Memory.builder().user(user).title(title).memoryDate(date).build());
  }

  private Memory save(User user, LocalDate date) {
    return memoryRepository.save(Memory.builder().user(user).title("추억").memoryDate(date).build());
  }

  private TimeCapsule saveCapsule() {
    LocalDateTime openAt = LocalDateTime.of(2045, 1, 1, 0, 0);
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(CapsuleStatus.WRITING)
            .letterDeadlineAt(openAt.minusDays(10))
            .openAt(openAt)
            .build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    memoryRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
