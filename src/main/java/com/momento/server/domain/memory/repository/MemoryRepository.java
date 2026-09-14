package com.momento.server.domain.memory.repository;

import com.momento.server.domain.memory.entity.Memory;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

  /** 아카이브 탭에 노출할 "내가 직접 등록한 추억" 목록. 소프트 삭제된 것과 타임캡슐에서 옮겨진 것은 제외한다. */
  @Query(
      value =
          """
                    SELECT m FROM Memory m
                    WHERE m.user.id = :userId
                      AND m.deletedAt IS NULL
                      AND m.timeCapsule IS NULL
                      AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
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
                      AND (:title IS NULL OR m.title LIKE CONCAT('%', :title, '%'))
                      AND (:startDate IS NULL OR m.memoryDate >= :startDate)
                      AND (:endDate IS NULL OR m.memoryDate <= :endDate)
                    """)
  Page<Memory> findMyMemories(
      @Param("userId") Long userId,
      @Param("title") String title,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      Pageable pageable);
}
