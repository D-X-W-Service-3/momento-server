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

  /** 캡슐 잠금 이후 호출한다. MySQL REPEATABLE READ에서도 오래된 스냅샷으로 중복을 놓치지 않는 현재 읽기다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from Letter l where l.timeCapsule.id = :capsuleId and l.author.id = :authorId and l.deletedAt is null")
  Optional<Letter> findActiveForUpdate(
      @Param("capsuleId") Long capsuleId, @Param("authorId") Long authorId);
}
