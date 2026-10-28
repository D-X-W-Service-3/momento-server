package com.momento.server.domain.letter.repository;

import com.momento.server.domain.letter.entity.Letter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterRepository extends JpaRepository<Letter, Long> {
  Optional<Letter> findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(Long capsuleId, Long authorId);

  /** 이 캡슐에 내 편지가 있는지. 임시 저장도 편지로 본다. 편지는 1인 1통이라 있으면 더 쓸 수 없다. */
  boolean existsByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(Long capsuleId, Long authorId);

  /**
   * 캡슐에 담긴 삭제되지 않은 편지 수. 임시 저장을 포함한다. 캡슐 삭제 조건("아무도 편지를 쓰지 않았을 때")에 쓴다. 상세 · 카드의 {@code
   * letterCount} 는 제출된 편지만 세므로 기준이 다르다.
   */
  long countByTimeCapsuleIdAndDeletedAtIsNull(Long capsuleId);

  /** 캡슐 잠금 이후 호출한다. MySQL REPEATABLE READ에서도 오래된 스냅샷으로 중복을 놓치지 않는 현재 읽기다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from Letter l where l.timeCapsule.id = :capsuleId and l.author.id = :authorId and l.deletedAt is null")
  Optional<Letter> findActiveForUpdate(
      @Param("capsuleId") Long capsuleId, @Param("authorId") Long authorId);
}
