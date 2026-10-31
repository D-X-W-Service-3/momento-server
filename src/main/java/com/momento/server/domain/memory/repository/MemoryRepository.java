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
   * 아카이브 탭에 노출할 "내가 직접 등록한 추억" 목록. 소프트 삭제된 것과 타임캡슐에서 옮겨진 것은 제외한다. 검색어는 이름 · 내용 중 한 곳에만 있어도 찾는다.
   *
   * @param pattern {@link com.momento.server.global.common.util.LikePattern#contains} 로 만든 패턴. null
   *     이면 검색하지 않는다
   */
  @Query(
      value =
          """
                    SELECT m FROM Memory m
                    WHERE m.user.id = :userId
                      AND m.deletedAt IS NULL
                      AND m.timeCapsule IS NULL
                      AND (:pattern IS NULL
                           OR m.title LIKE :pattern ESCAPE '!'
                           OR m.content LIKE :pattern ESCAPE '!')
                      AND (:startDate IS NULL OR m.memoryDate >= :startDate)
                      AND (:endDate IS NULL OR m.memoryDate <= :endDate)
                    ORDER BY m.memoryDate DESC, m.id DESC
                    """,
      countQuery =
          """
                    SELECT COUNT(m) FROM Memory m
                    WHERE m.user.id = :userId
                      AND m.deletedAt IS NULL
                      AND m.timeCapsule IS NULL
                      AND (:pattern IS NULL
                           OR m.title LIKE :pattern ESCAPE '!'
                           OR m.content LIKE :pattern ESCAPE '!')
                      AND (:startDate IS NULL OR m.memoryDate >= :startDate)
                      AND (:endDate IS NULL OR m.memoryDate <= :endDate)
                    """)
  Page<Memory> findMyMemories(
      @Param("userId") Long userId,
      @Param("pattern") String pattern,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      Pageable pageable);

  /** 캘린더 월별 점. {@link #findMyMemories} 와 같은 추억 중 날짜가 {@code [from, to)} 인 것의 날짜만 읽는다. */
  @Query(
      """
          SELECT m.memoryDate FROM Memory m
          WHERE m.user.id = :userId
            AND m.deletedAt IS NULL
            AND m.timeCapsule IS NULL
            AND m.memoryDate >= :from
            AND m.memoryDate < :to
          """)
  List<LocalDate> findMyMemoryDatesBetween(
      @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /** 캘린더 날짜별 추억. {@link #findMyMemories} 와 같은 추억 중 그 날 것을 등록 순으로 읽는다. */
  @Query(
      """
          SELECT m FROM Memory m
          WHERE m.user.id = :userId
            AND m.deletedAt IS NULL
            AND m.timeCapsule IS NULL
            AND m.memoryDate = :date
          ORDER BY m.id
          """)
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
      """
          SELECT m FROM Memory m
          WHERE m.user.id = :userId
            AND m.deletedAt IS NULL
            AND m.timeCapsule IS NULL
            AND m.title LIKE :pattern ESCAPE '!'
            AND (:from IS NULL OR m.memoryDate >= :from)
            AND (:to IS NULL OR m.memoryDate < :to)
          ORDER BY m.memoryDate, m.id
          """)
  List<Memory> searchMyMemories(
      @Param("userId") Long userId,
      @Param("pattern") String pattern,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  /** 캘린더 탭 개수. {@link #findMyMemories} 에 검색 조건 없이 나오는 추억의 수다. */
  @Query(
      """
          SELECT COUNT(m) FROM Memory m
          WHERE m.user.id = :userId
            AND m.deletedAt IS NULL
            AND m.timeCapsule IS NULL
          """)
  long countMyMemories(@Param("userId") Long userId);
}
