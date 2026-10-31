package com.momento.server.domain.anniversary.repository;

import com.momento.server.domain.anniversary.entity.Anniversary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {

  /** 내 기념일을 조회한다. 없는 기념일과 다른 회원의 기념일은 똑같이 빈 결과다. */
  Optional<Anniversary> findByIdAndUserId(Long id, Long userId);

  /** 캘린더용. 반복 기념일의 날짜는 서버에서 계산하므로 내 기념일을 모두 읽는다. 회원당 수가 적다. */
  List<Anniversary> findByUserIdOrderByIdAsc(Long userId);

  long countByUserId(Long userId);

  /**
   * 통합 검색. 이름에 검색어가 들어 있는 내 기념일을 등록 순으로 읽는다. 날짜는 계산값이라 기간 · 정렬은 서버에서 한다.
   *
   * @param pattern {@link com.momento.server.global.common.util.LikePattern#contains} 로 만든 패턴
   */
  @Query(
      "select a from Anniversary a"
          + " where a.user.id = :userId and a.title like :pattern escape '!'"
          + " order by a.id")
  List<Anniversary> searchMine(@Param("userId") Long userId, @Param("pattern") String pattern);
}
