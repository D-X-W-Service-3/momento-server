package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {
  Optional<TimeCapsule> findByIdAndDeletedAtIsNull(Long id);

  /** 같은 캡슐의 편지 생성은 이 잠금을 먼저 얻고 중복 확인과 저장까지 한 트랜잭션으로 처리한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from TimeCapsule c where c.id = :id and c.deletedAt is null")
  Optional<TimeCapsule> findActiveByIdForUpdate(@Param("id") Long id);
}
