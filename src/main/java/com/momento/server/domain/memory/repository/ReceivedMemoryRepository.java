package com.momento.server.domain.memory.repository;

import com.momento.server.domain.memory.entity.ReceivedMemory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceivedMemoryRepository extends JpaRepository<ReceivedMemory, Long> {

  /** 받은 아카이브 조회에 나오는 것. 아직 저장 · 닫기 하지 않았고 원본이 삭제되지 않았다. */
  String PENDING_FOR_RECEIVER =
      " from ReceivedMemory r join r.memory m"
          + " where r.receiver.id = :receiverId"
          + " and r.status = com.momento.server.domain.memory.entity.ReceivedMemoryStatus.PENDING"
          + " and m.deletedAt is null";

  /**
   * 링크로 받은 기록을 남긴다. 이미 있으면 아무것도 하지 않는다. "있는지 보고 없으면 저장" 은 같은 회원이 링크를 동시에 두 번 열면 UNIQUE 위반 500 이 나서,
   * 한 문장으로 넣는다.
   *
   * @return 실제로 넣은 행 수. 처음 받으면 1, 전에 받았으면 0
   */
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO received_memories (memory_id, receiver_id, status, created_at, updated_at)"
              + " VALUES (:memoryId, :receiverId, 'PENDING', :now, :now)",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("memoryId") Long memoryId,
      @Param("receiverId") Long receiverId,
      @Param("now") LocalDateTime now);

  Optional<ReceivedMemory> findByMemoryIdAndReceiverId(Long memoryId, Long receiverId);

  /** 받은 아카이브 조회. 보낸 사람 닉네임까지 한 번에 읽고, 받은 순이다. */
  @Query(
      "select r from ReceivedMemory r join fetch r.memory m join fetch m.user"
          + " where r.receiver.id = :receiverId"
          + " and r.status = com.momento.server.domain.memory.entity.ReceivedMemoryStatus.PENDING"
          + " and m.deletedAt is null"
          + " order by r.id")
  List<ReceivedMemory> findPending(@Param("receiverId") Long receiverId);

  /** 저장 · 닫기 대상. 받은 아카이브 조회에 나오는 것만 처리할 수 있다. */
  @Query("select r" + PENDING_FOR_RECEIVER + " and r.id = :id")
  Optional<ReceivedMemory> findPendingById(
      @Param("id") Long id, @Param("receiverId") Long receiverId);

  /** 아카이브 목록의 "공유받은 아카이브가 있어요" 배너. */
  @Query("select count(r) > 0" + PENDING_FOR_RECEIVER)
  boolean existsPending(@Param("receiverId") Long receiverId);
}
