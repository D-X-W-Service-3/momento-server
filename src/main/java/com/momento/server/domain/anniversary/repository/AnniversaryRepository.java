package com.momento.server.domain.anniversary.repository;

import com.momento.server.domain.anniversary.entity.Anniversary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {

  /** 내 기념일을 조회한다. 없는 기념일과 다른 회원의 기념일은 똑같이 빈 결과다. */
  Optional<Anniversary> findByIdAndUserId(Long id, Long userId);
}
