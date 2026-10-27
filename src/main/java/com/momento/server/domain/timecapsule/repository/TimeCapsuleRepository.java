package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {

  /** 내가 ACTIVE 멤버이고 삭제되지 않은 캡슐. 목록 · 탭 홈 조회가 모두 여기서 시작한다. */
  String MY_ACTIVE_CAPSULES =
      " from CapsuleMember m join m.timeCapsule c"
          + " where m.user.id = :userId"
          + " and m.status = com.momento.server.domain.timecapsule.entity.MemberStatus.ACTIVE"
          + " and c.deletedAt is null";

  /**
   * 캡슐 이름 · 설명 · ACTIVE 멤버 닉네임 중 하나에 검색어가 들어 있는 캡슐. {@code :keyword} 가 null 이면 거르지 않는다. 닉네임은 조인하면
   * 여러 명이 맞을 때 같은 캡슐이 여러 번 나오므로 서브쿼리로 찾는다.
   */
  String KEYWORD_MATCHES =
      " and (:keyword is null"
          + " or c.title like :keyword escape '!'"
          + " or c.description like :keyword escape '!'"
          + " or exists (select km.id from CapsuleMember km join km.user ku"
          + " where km.timeCapsule = c"
          + " and km.status = com.momento.server.domain.timecapsule.entity.MemberStatus.ACTIVE"
          + " and ku.nickname like :keyword escape '!'))";

  /**
   * 검색어를 "포함" 검색용 LIKE 패턴으로 바꾼다. {@code %} · {@code _} 를 이스케이프하지 않으면 {@code %} 하나로 모든 캡슐이 나온다. 비어
   * 있으면 null 이라 검색하지 않는다.
   */
  static String containsPattern(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String escaped = keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  /**
   * 내가 속한 캡슐을 개봉일 · ID 오름차순으로 읽는다. 커서는 직전 페이지 마지막 캡슐의 (개봉일, ID) 다. 개봉일은 겹칠 수 있어서 개봉일만으로는 페이지 경계의
   * 캡슐이 빠지거나 겹친다.
   *
   * @param status null 이면 전체
   * @param keyword {@link #containsPattern} 으로 만든 패턴. null 이면 검색하지 않는다
   * @param cursorOpenAt null 이면 첫 페이지
   */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.MyCapsule(c, m.openedAt)"
          + MY_ACTIVE_CAPSULES
          + " and (:status is null or c.status = :status)"
          + KEYWORD_MATCHES
          + " and (:cursorOpenAt is null or c.openAt > :cursorOpenAt"
          + " or (c.openAt = :cursorOpenAt and c.id > :cursorId))"
          + " order by c.openAt, c.id")
  List<MyCapsule> findMyCapsules(
      @Param("userId") Long userId,
      @Param("status") CapsuleStatus status,
      @Param("keyword") String keyword,
      @Param("cursorOpenAt") LocalDateTime cursorOpenAt,
      @Param("cursorId") Long cursorId,
      Limit limit);

  /** 탭 홈 "곧 만날 타임캡슐". 내가 아직 확인하지 않은 캡슐을 개봉일이 가까운 순으로 읽는다. 열렸지만 확인하지 않은 캡슐은 개봉일이 지나 있으므로 맨 앞에 온다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.MyCapsule(c, m.openedAt)"
          + MY_ACTIVE_CAPSULES
          + " and m.openedAt is null"
          + " order by c.openAt, c.id")
  List<MyCapsule> findMyUnopenedCapsules(@Param("userId") Long userId, Limit limit);

  /** 탭 홈 "수정할 수 있어요". 작성 중인 캡슐을 편지 마감이 가까운 순으로 읽는다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.MyCapsule(c, m.openedAt)"
          + MY_ACTIVE_CAPSULES
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING"
          + " order by c.letterDeadlineAt, c.id")
  List<MyCapsule> findMyWritingCapsules(@Param("userId") Long userId, Limit limit);

  /** 탭 홈 "추억이 열렸어요". 열린 캡슐을 최근에 열린 순으로 읽는다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.MyCapsule(c, m.openedAt)"
          + MY_ACTIVE_CAPSULES
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.OPENED"
          + " order by c.openAt desc, c.id desc")
  List<MyCapsule> findMyOpenedCapsules(@Param("userId") Long userId, Limit limit);

  /** 내가 속한 캡슐의 상태별 개수. 검색어는 적용하고, 상태 탭과 커서는 적용하지 않는다. 캡슐이 없는 상태는 결과에 없다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.CapsuleStatusCount("
          + "c.status, count(c))"
          + MY_ACTIVE_CAPSULES
          + KEYWORD_MATCHES
          + " group by c.status")
  List<CapsuleStatusCount> countMyCapsulesByStatus(
      @Param("userId") Long userId, @Param("keyword") String keyword);

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
   * 편지 마감이 지났지만 아직 공개 시각은 되지 않은 캡슐을 잠근다.
   *
   * @return 실제로 바뀐 행 수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update TimeCapsule c"
          + " set c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.LOCKED"
          + " where c.deletedAt is null"
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING"
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

  /** {@link #countLettersByStatus} 를 캡슐 여러 개에 한 번에 센다. 편지가 없는 캡슐은 결과에 없다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.CapsuleCount("
          + "l.timeCapsule.id, count(l))"
          + " from Letter l"
          + " where l.timeCapsule.id in :capsuleIds and l.status = :status and l.deletedAt is null"
          + " group by l.timeCapsule.id")
  List<CapsuleCount> countLettersByStatusIn(
      @Param("capsuleIds") Collection<Long> capsuleIds, @Param("status") LetterStatus status);

  /** 같은 캡슐의 편지 생성은 이 잠금을 먼저 얻고 중복 확인과 저장까지 한 트랜잭션으로 처리한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from TimeCapsule c where c.id = :id and c.deletedAt is null")
  Optional<TimeCapsule> findActiveByIdForUpdate(@Param("id") Long id);
}
