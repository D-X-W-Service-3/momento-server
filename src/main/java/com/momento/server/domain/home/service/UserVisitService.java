package com.momento.server.domain.home.service;

import com.momento.server.domain.home.entity.VisitHistory;
import com.momento.server.domain.home.repository.UserVisitRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserVisitService {

  private final UserVisitRepository userVisitRepository;

  /**
   * 오늘 방문을 남기고 지금까지 들른 날을 읽는다. 홈 조회가 부른다. 먼저 남기므로 돌려주는 기록에는 오늘이 늘 들어 있다.
   *
   * @param now 호출하는 쪽이 {@code LocalDateTime.now(clock)} 으로 넘긴다. 날짜 부분이 오늘이다
   */
  @Transactional
  public VisitHistory visit(Long userId, LocalDateTime now) {
    userVisitRepository.insertIfAbsent(userId, now.toLocalDate(), now);
    return VisitHistory.of(userVisitRepository.findVisitDates(userId));
  }
}
