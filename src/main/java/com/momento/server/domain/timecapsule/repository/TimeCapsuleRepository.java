package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {

  Optional<TimeCapsule> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 공개 시각이 지났는데 아직 열리지 않은 캡슐의 ID. 공개 알림을 보낼 대상이라 상태를 바꾸기 전에 뽑아 둔다. 삭제된 캡슐은 제외한다.
   *
   * <p>"열리지 않은" 을 {@code <> OPENED} 가 아니라 {@code IN (WRITING, LOCKED)} 으로 쓴다. 뜻은 같지만 {@code
   * (status, open_at)} 인덱스에서 선행 컬럼이 부등호면 범위를 좁히지 못해 뒤의 {@code open_at} 까지 인덱스로 가지 못한다.
   */
  @Query(
      "select c.id from TimeCapsule c"
          + " where c.deletedAt is null"
          + " and c.status in (com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING,"
          + " com.momento.server.domain.timecapsule.entity.CapsuleStatus.LOCKED)"
          + " and c.openAt <= :now")
  List<Long> findIdsToOpen(@Param("now") LocalDateTime now);

  /**
   * 주어진 캡슐을 연다. 조건을 UPDATE 에 함께 넣어, 이미 열린 캡슐을 다시 건드리지 않는다. 조회한 뒤 저장하면 같은 캡슐이 두 번 열릴 여지가 생긴다.
   *
   * <p>여기는 {@code c.id in :ids} 가 기본키로 먼저 좁히므로 상태 조건이 부등호여도 인덱스에 영향이 없다.
   *
   * @return 실제로 바뀐 행 수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update TimeCapsule c"
          + " set c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.OPENED"
          + " where c.id in :ids"
          + " and c.status <> com.momento.server.domain.timecapsule.entity.CapsuleStatus.OPENED")
  int openAll(@Param("ids") List<Long> ids);

  /**
   * 편지 마감이 지났지만 아직 공개 시각은 되지 않은 캡슐을 잠근다. 마감이 없는 캡슐({@code letter_deadline_at IS NULL})은 잠기지 않고 공개
   * 시각에 바로 열린다.
   *
   * @return 실제로 바뀐 행 수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update TimeCapsule c"
          + " set c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.LOCKED"
          + " where c.deletedAt is null"
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING"
          + " and c.letterDeadlineAt is not null"
          + " and c.letterDeadlineAt <= :now")
  int lockAllPastDeadline(@Param("now") LocalDateTime now);

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
