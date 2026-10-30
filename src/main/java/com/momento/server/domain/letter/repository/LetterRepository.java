package com.momento.server.domain.letter.repository;

import com.momento.server.domain.letter.entity.Letter;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterRepository extends JpaRepository<Letter, Long> {
  /** 권한 범위를 DB에서 제한하고 작성자를 함께 읽어 목록 변환 시 추가 조회를 방지한다. */
  @Query(
      "select l from Letter l join fetch l.author where l.timeCapsule.id = :capsuleId"
          + " and l.deletedAt is null and l.status = com.momento.server.domain.letter.entity.LetterStatus.SUBMITTED"
          + " and (:viewAll = true or l.author.id = :userId) order by l.createdAt asc, l.id asc")
  List<Letter> findVisibleSubmittedLetters(
      @Param("capsuleId") Long capsuleId,
      @Param("userId") Long userId,
      @Param("viewAll") boolean viewAll);

  Optional<Letter> findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(Long capsuleId, Long authorId);

  /** 캡슐을 먼저 잠그기 위한 식별자 조회. 편지 엔티티를 1차 캐시에 올리지 않는다. */
  @Query(
      "select l.timeCapsule.id from Letter l where l.id = :letterId and l.author.id = :authorId and l.deletedAt is null")
  Optional<Long> findCapsuleIdForDeletion(
      @Param("letterId") Long letterId, @Param("authorId") Long authorId);

  /** 캡슐 잠금 뒤 요청한 ID의 편지만 현재 읽기로 재확인한다. 새로 작성한 다른 편지를 지우지 않는다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from Letter l where l.id = :letterId and l.author.id = :authorId and l.timeCapsule.id = :capsuleId and l.deletedAt is null")
  Optional<Letter> findActiveByIdForUpdate(
      @Param("letterId") Long letterId,
      @Param("authorId") Long authorId,
      @Param("capsuleId") Long capsuleId);

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

  /**
   * 내보낸 멤버가 이 캡슐에 쓴 편지(임시 저장 포함)를 소프트 삭제한다. 일괄 UPDATE 는 JPA Auditing 을 거치지 않아 {@code updatedAt} 도
   * 직접 채운다. 앞서 바꾼 멤버 상태를 먼저 flush 하고, 끝나면 영속성 컨텍스트를 비워 지운 편지가 1차 캐시에 남지 않게 한다.
   *
   * @return 지운 편지 수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update Letter l set l.deletedAt = :now, l.updatedAt = :now"
          + " where l.timeCapsule.id = :capsuleId and l.author.id = :authorId and l.deletedAt is null")
  int softDeleteByAuthor(
      @Param("capsuleId") Long capsuleId,
      @Param("authorId") Long authorId,
      @Param("now") LocalDateTime now);
}
