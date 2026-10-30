package com.momento.server.domain.home.repository;

import com.momento.server.domain.home.entity.UserVisit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVisitRepository extends JpaRepository<UserVisit, Long> {

  /**
   * 그날 방문을 남긴다. 이미 있으면 아무것도 하지 않는다. "있는지 보고 없으면 저장" 은 같은 회원이 동시에 두 번 부르면 둘 다 없다고 보고 저장해 UNIQUE 위반
   * 500 이 나서, 한 문장으로 넣는다.
   *
   * @return 실제로 넣은 행 수. 그날 처음이면 1, 이미 들렀으면 0
   */
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO user_visits (user_id, visit_date, created_at)"
              + " VALUES (:userId, :visitDate, :now)",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("userId") Long userId,
      @Param("visitDate") LocalDate visitDate,
      @Param("now") LocalDateTime now);

  /** 회원이 들른 날 전부. 회원당 하루 한 행이라 행 수가 방문 일수다. */
  @Query("select v.visitDate from UserVisit v where v.user.id = :userId")
  List<LocalDate> findVisitDates(@Param("userId") Long userId);
}
