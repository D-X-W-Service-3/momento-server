package com.momento.server.domain.memory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import com.momento.server.domain.memory.entity.ReceivedMemoryStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/** "내 아카이브" 에 공유받아 저장한 추억이 함께 들어가는지 검증한다. 받기만 하거나 닫은 추억은 들어가지 않는다. */
@SpringBootTest
class SavedMemoryQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);
  private static final LocalDate DAY = LocalDate.of(2026, 8, 15);

  @Autowired private MemoryRepository memoryRepository;
  @Autowired private ReceivedMemoryRepository receivedMemoryRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User friend;
  private Memory saved;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("saved-me", "나");
    friend = saveUser("saved-friend", "친구");
    memory(me, "내 추억");
    saved = memory(friend, "저장한 추억");
    receive(saved, ReceivedMemoryStatus.SAVED);
    receive(memory(friend, "받기만 한 추억"), ReceivedMemoryStatus.PENDING);
    receive(memory(friend, "닫은 추억"), ReceivedMemoryStatus.DISMISSED);
    memory(friend, "친구만의 추억");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("아카이브 목록에 내 추억과 저장한 추억이 함께 나오고 작성자를 함께 읽는다")
  void archiveList() {
    var page = memoryRepository.findMyMemories(me.getId(), null, null, null, PageRequest.of(0, 20));

    assertThat(page.getContent())
        .extracting(Memory::getTitle)
        .containsExactlyInAnyOrder("내 추억", "저장한 추억");
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent())
        .filteredOn(memory -> memory.getId().equals(saved.getId()))
        .singleElement()
        .extracting(memory -> memory.getUser().getNickname())
        .isEqualTo("친구");
  }

  @Test
  @DisplayName("캘린더 · 통합 검색 · 개수도 같은 기준이다")
  void calendarAndSearch() {
    assertThat(memoryRepository.findMyMemoryDatesBetween(me.getId(), DAY, DAY.plusDays(1)))
        .hasSize(2);
    assertThat(memoryRepository.findMyMemoriesOn(me.getId(), DAY))
        .extracting(Memory::getTitle)
        .containsExactly("내 추억", "저장한 추억");
    assertThat(memoryRepository.searchMyMemories(me.getId(), "%추억%", null, null))
        .extracting(Memory::getTitle)
        .containsExactly("내 추억", "저장한 추억");
    assertThat(memoryRepository.countMyMemories(me.getId())).isEqualTo(2);
  }

  @Test
  @DisplayName("원본이 삭제되면 저장한 사람 쪽에서도 빠진다")
  void deletedOriginal() {
    saved.delete(NOW);
    memoryRepository.save(saved);

    assertThat(memoryRepository.countMyMemories(me.getId())).isEqualTo(1);
    assertThat(memoryRepository.findMyMemoriesOn(me.getId(), DAY))
        .extracting(Memory::getTitle)
        .containsExactly("내 추억");
  }

  @Test
  @DisplayName("공유한 사람의 아카이브에는 받은 사람이 저장했다고 달라지는 것이 없다")
  void ownerUnaffected() {
    assertThat(memoryRepository.countMyMemories(friend.getId())).isEqualTo(4);
  }

  private void receive(Memory memory, ReceivedMemoryStatus status) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          receivedMemoryRepository.insertIfAbsent(memory.getId(), me.getId(), NOW);
          ReceivedMemory received =
              receivedMemoryRepository
                  .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
                  .orElseThrow();
          if (status == ReceivedMemoryStatus.SAVED) {
            received.save();
          } else if (status == ReceivedMemoryStatus.DISMISSED) {
            received.dismiss();
          }
        });
  }

  private Memory memory(User owner, String title) {
    return memoryRepository.save(Memory.builder().user(owner).title(title).memoryDate(DAY).build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    receivedMemoryRepository.deleteAllInBatch();
    memoryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
