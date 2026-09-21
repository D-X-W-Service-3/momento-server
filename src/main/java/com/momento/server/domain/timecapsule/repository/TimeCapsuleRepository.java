package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {

  Optional<TimeCapsule> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 캡슐에 담긴 편지 중 주어진 상태이고 삭제되지 않은 것의 수. 편지 도메인에 Repository 가 아직 없어 임시로 여기 둔다. 편지 API 작업에서 {@code
   * LetterRepository} 가 생기면 그쪽으로 옮긴다.
   */
  @Query(
      "select count(l) from Letter l"
          + " where l.timeCapsule.id = :capsuleId and l.status = :status and l.deletedAt is null")
  long countLettersByStatus(
      @Param("capsuleId") Long capsuleId, @Param("status") LetterStatus status);

  /** 같은 캡슐의 편지 생성은 이 잠금을 먼저 얻고 중복 확인과 저장까지 한 트랜잭션으로 처리한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from TimeCapsule c where c.id = :id and c.deletedAt is null")
  Optional<TimeCapsule> findActiveByIdForUpdate(@Param("id") Long id);
}
