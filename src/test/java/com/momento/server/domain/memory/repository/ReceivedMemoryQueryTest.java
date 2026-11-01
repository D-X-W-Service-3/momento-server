package com.momento.server.domain.memory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import com.momento.server.domain.memory.entity.ReceivedMemoryStatus;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/** 아카이브 공유와 받은 아카이브 쿼리를 검증한다. */
@SpringBootTest
class ReceivedMemoryQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

  @Autowired private ReceivedMemoryRepository receivedMemoryRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("received-me", "나");
    friend = saveUser("received-friend", "친구");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("링크로 받은 기록은 같은 회원이 여러 번 넣어도 한 행이다")
  void insertsOncePerReceiver() {
    Memory memory = memory(friend, "친구 추억");

    assertThat(receive(memory, me)).isEqualTo(1);
    assertThat(receive(memory, me)).isZero();
    assertThat(receive(memory, saveUser("received-third", "셋째"))).isEqualTo(1);

    assertThat(receivedMemoryRepository.findByMemoryIdAndReceiverId(memory.getId(), me.getId()))
        .get()
        .extracting(ReceivedMemory::getStatus)
        .isEqualTo(ReceivedMemoryStatus.PENDING);
  }

  @Test
  @DisplayName("받은 아카이브는 아직 처리하지 않았고 원본이 남아 있는 것만 받은 순으로 읽는다")
  void findsPendingInReceivedOrder() {
    Memory first = memory(friend, "첫째");
    Memory second = memory(friend, "둘째");
    Memory saved = memory(friend, "저장함");
    Memory dismissed = memory(friend, "닫음");
    Memory deleted = memory(friend, "지워짐");
    Memory othersReceived = memory(friend, "남이 받음");
    receive(first, me);
    receive(second, me);
    receive(saved, me);
    receive(dismissed, me);
    receive(deleted, me);
    receive(othersReceived, saveUser("received-third", "셋째"));
    change(saved, ReceivedMemoryStatus.SAVED);
    change(dismissed, ReceivedMemoryStatus.DISMISSED);
    deleted.delete(NOW);
    memoryRepository.save(deleted);

    List<ReceivedMemory> pending = receivedMemoryRepository.findPending(me.getId());

    assertThat(pending)
        .extracting(received -> received.getMemory().getTitle())
        .containsExactly("첫째", "둘째");
    assertThat(pending.get(0).getMemory().getUser().getNickname()).isEqualTo("친구");
    assertThat(receivedMemoryRepository.existsPending(me.getId())).isTrue();
  }

  @Test
  @DisplayName("받은 아카이브가 없으면 배너 여부는 false 다")
  void noPending() {
    Memory saved = memory(friend, "저장함");
    receive(saved, me);
    change(saved, ReceivedMemoryStatus.SAVED);

    assertThat(receivedMemoryRepository.existsPending(me.getId())).isFalse();
  }

  @Test
  @DisplayName("저장 · 닫기 대상은 내 받은 아카이브 중 처리하지 않은 것만 찾는다")
  void findsPendingById() {
    Memory memory = memory(friend, "친구 추억");
    receive(memory, me);
    ReceivedMemory mine =
        receivedMemoryRepository
            .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
            .orElseThrow();

    assertThat(receivedMemoryRepository.findPendingById(mine.getId(), me.getId())).isPresent();
    assertThat(receivedMemoryRepository.findPendingById(mine.getId(), friend.getId())).isEmpty();

    change(memory, ReceivedMemoryStatus.SAVED);
    assertThat(receivedMemoryRepository.findPendingById(mine.getId(), me.getId())).isEmpty();
  }

  @Test
  @DisplayName("공유 토큰으로는 삭제되지 않은 추억만 찾는다")
  void findsByShareToken() {
    Memory shared = memory(friend, "공유함");
    shared.share(() -> "token-live");
    memoryRepository.save(shared);
    Memory deleted = memory(friend, "지워짐");
    deleted.share(() -> "token-deleted");
    deleted.delete(NOW);
    memoryRepository.save(deleted);

    assertThat(memoryRepository.findByShareTokenAndDeletedAtIsNull("token-live"))
        .get()
        .extracting(Memory::getId)
        .isEqualTo(shared.getId());
    assertThat(memoryRepository.findByShareTokenAndDeletedAtIsNull("token-deleted")).isEmpty();
    assertThat(memoryRepository.findByShareTokenAndDeletedAtIsNull("token-none")).isEmpty();
  }

  @Test
  @DisplayName("공유 링크 발급 대상은 내 추억만 찾는다")
  void findsMineForUpdate() {
    Memory mine = memory(me, "내 추억");
    Memory others = memory(friend, "친구 추억");

    assertThat(lockMine(mine.getId())).isTrue();
    assertThat(lockMine(others.getId())).isFalse();
  }

  private boolean lockMine(Long memoryId) {
    return transactionTemplate.execute(
        tx -> memoryRepository.findMineForUpdate(memoryId, me.getId()).isPresent());
  }

  private int receive(Memory memory, User receiver) {
    return transactionTemplate.execute(
        tx -> receivedMemoryRepository.insertIfAbsent(memory.getId(), receiver.getId(), NOW));
  }

  private void change(Memory memory, ReceivedMemoryStatus status) {
    ReceivedMemory received =
        receivedMemoryRepository
            .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
            .orElseThrow();
    if (status == ReceivedMemoryStatus.SAVED) {
      received.save();
    } else {
      received.dismiss();
    }
    receivedMemoryRepository.save(received);
  }

  private Memory memory(User owner, String title) {
    return memoryRepository.save(
        Memory.builder().user(owner).title(title).memoryDate(LocalDate.of(2026, 8, 15)).build());
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
