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

  /** 수정 후 상세와 같은 형태를 돌려준다. 수정 화면이 저장 뒤 다시 조회하지 않아도 되게 한다. */
  public AnniversaryDetailResponse update(
      Long anniversaryId, Long userId, AnniversaryRequest request) {
    return AnniversaryDetailResponse.of(
        anniversaryService.update(anniversaryId, userId, request), LocalDate.now(clock));
  }

  public void delete(Long anniversaryId, Long userId) {
    anniversaryService.delete(anniversaryId, userId);
  }
}
