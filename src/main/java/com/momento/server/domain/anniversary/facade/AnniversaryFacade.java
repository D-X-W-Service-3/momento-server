package com.momento.server.domain.anniversary.facade;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.dto.response.AnniversaryCreateResponse;
import com.momento.server.domain.anniversary.dto.response.AnniversaryDetailResponse;
import com.momento.server.domain.anniversary.service.AnniversaryService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnniversaryFacade {

  private final UserService userService;
  private final AnniversaryService anniversaryService;
  private final Clock clock;

  public AnniversaryCreateResponse create(Long userId, AnniversaryRequest request) {
    User user = userService.getActiveUser(userId);

    return AnniversaryCreateResponse.from(anniversaryService.create(user, request));
  }

  /** {@code nextDate} · {@code dDay} 는 저장하지 않고 조회할 때 오늘을 기준으로 계산한다. */
  public AnniversaryDetailResponse getDetail(Long anniversaryId, Long userId) {
    return AnniversaryDetailResponse.of(
        anniversaryService.getMine(anniversaryId, userId), LocalDate.now(clock));
  }

  public void update(Long anniversaryId, Long userId, AnniversaryRequest request) {
    anniversaryService.update(anniversaryId, userId, request);
  }

  public void delete(Long anniversaryId, Long userId) {
    anniversaryService.delete(anniversaryId, userId);
  }
}
