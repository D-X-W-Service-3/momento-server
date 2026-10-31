package com.momento.server.domain.memory.repository;

import com.momento.server.domain.memory.entity.Memory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

  /**
   * 상세 · 수정 · 삭제 대상. 목록 · 캘린더와 같이 내가 직접 등록했고 삭제되지 않은 아카이브만 찾는다. 타임캡슐에서 옮겨진 추억은 목록에 보이지 않으므로 ID 로도
   * 찾지 않는다.
   */
  Optional<Memory> findByIdAndUserIdAndTimeCapsuleIsNullAndDeletedAtIsNull(Long id, Long userId);

  /** 공유 링크 발급 대상. 상세 · 수정 · 삭제와 같은 내 아카이브이고, 두 요청이 동시에 토큰을 만들어 먼저 받은 토큰이 덮이지 않게 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from Memory m where m.id = :id and m.user.id = :userId"
          + " and m.timeCapsule is null and m.deletedAt is null")
  Optional<Memory> findMineForUpdate(@Param("id") Long id, @Param("userId") Long userId);

  /** 공유 링크로 받을 추억. 삭제된 추억의 링크는 없는 링크와 같다. */
  Optional<Memory> findByShareTokenAndDeletedAtIsNull(String shareToken);

  /**
   * 아카이브 목록 · 캘린더 · 통합 검색 · 개수가 함께 쓰는 "내 아카이브" 조건. 삭제되지 않았고 타임캡슐에서 옮겨지지 않은, 내가 등록한 추억이다. 조건이 바뀌면
   * 여기만 고친다.
   */
  String MY_ARCHIVE =
      " where m.deletedAt is null" + " and m.timeCapsule is null" + " and m.user.id = :userId";

  /** 아카이브 목록의 검색어 · 연도 조건. 목록과 목록 개수가 같이 쓴다. */
  String ARCHIVE_LIST_FILTER =
      " and (:pattern is null"
          + " or m.title like :pattern escape '!'"
          + " or m.content like :pattern escape '!')"
          + " and (:startDate is null or m.memoryDate >= :startDate)"
          + " and (:endDate is null or m.memoryDate <= :endDate)";

  /**
   * 아카이브 탭 목록. 소프트 삭제된 것과 타임캡슐에서 옮겨진 것은 제외한다. 검색어는 이름 · 내용 중 한 곳에만 있어도 찾는다.
   *
   * @param pattern {@link com.momento.server.global.common.util.LikePattern#contains} 로 만든 패턴. null
   *     이면 검색하지 않는다
   */
  @Query(
      value =
          "select m from Memory m"
              + MY_ARCHIVE
              + ARCHIVE_LIST_FILTER
              + " order by m.memoryDate desc, m.id desc",
      countQuery = "select count(m) from Memory m" + MY_ARCHIVE + ARCHIVE_LIST_FILTER)
  Page<Memory> findMyMemories(
      @Param("userId") Long userId,
      @Param("pattern") String pattern,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      Pageable pageable);

  /** 캘린더 월별 점. {@link #findMyMemories} 와 같은 추억 중 날짜가 {@code [from, to)} 인 것의 날짜만 읽는다. */
  @Query(
      "select m.memoryDate from Memory m"
          + MY_ARCHIVE
          + " and m.memoryDate >= :from and m.memoryDate < :to")
  List<LocalDate> findMyMemoryDatesBetween(
      @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /** 캘린더 날짜별 추억. {@link #findMyMemories} 와 같은 추억 중 그 날 것을 등록 순으로 읽는다. */
  @Query("select m from Memory m" + MY_ARCHIVE + " and m.memoryDate = :date order by m.id")
  List<Memory> findMyMemoriesOn(@Param("userId") Long userId, @Param("date") LocalDate date);

  /**
   * 통합 검색. {@link #findMyMemories} 와 같은 추억 중 제목에 검색어가 들어 있고 날짜가 {@code [from, to)} 인 것을 날짜 · ID
   * 오름차순으로 읽는다. 최신순은 서비스가 뒤집는다.
   *
   * @param pattern {@link com.momento.server.global.common.util.LikePattern#contains} 로 만든 패턴
   * @param from null 이면 처음부터
   * @param to null 이면 끝까지
   */
  @Query(
      "select m from Memory m"
          + MY_ARCHIVE
          + " and m.title like :pattern escape '!'"
          + " and (:from is null or m.memoryDate >= :from)"
          + " and (:to is null or m.memoryDate < :to)"
          + " order by m.memoryDate, m.id")
  List<Memory> searchMyMemories(
      @Param("userId") Long userId,
      @Param("pattern") String pattern,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  /** 캘린더 탭 개수. {@link #findMyMemories} 에 검색 조건 없이 나오는 추억의 수다. */
  @Query("select count(m) from Memory m" + MY_ARCHIVE)
  long countMyMemories(@Param("userId") Long userId);
}
