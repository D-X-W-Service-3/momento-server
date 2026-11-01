package com.momento.server.domain.anniversary.service;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.exception.AnniversaryErrorCode;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.exception.ApiException;
import com.momento.server.global.common.util.LikePattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnniversaryService {

  private final AnniversaryRepository anniversaryRepository;

  @Transactional
  public Anniversary create(User user, AnniversaryRequest request) {
    return anniversaryRepository.save(
        Anniversary.create(
            user,
            request.title(),
            request.anniversaryDate(),
            request.repeatType(),
            request.memo(),
            request.notificationEnabled(),
            request.notifyTime(),
            request.remindDaysBefore()));
  }

  /** 내 기념일을 조회한다. 없는 기념일과 다른 회원의 기념일은 똑같이 404 다. */
  public Anniversary getMine(Long anniversaryId, Long userId) {
    return anniversaryRepository
        .findByIdAndUserId(anniversaryId, userId)
        .orElseThrow(() -> new ApiException(AnniversaryErrorCode.ANNIVERSARY_NOT_FOUND));
  }

  /** 캘린더용. 날짜는 {@link Anniversary#dateIn} 으로 계산하므로 내 기념일을 모두 읽는다. */
  public List<Anniversary> getAllMine(Long userId) {
    return anniversaryRepository.findByUserIdOrderByIdAsc(userId);
  }

  public long countMine(Long userId) {
    return anniversaryRepository.countByUserId(userId);
  }

  /** 통합 검색. 이름에 검색어가 든 내 기념일. 기간 · 정렬은 날짜를 계산하는 쪽이 한다. */
  public List<Anniversary> searchMine(Long userId, String keyword) {
    return anniversaryRepository.searchMine(userId, LikePattern.contains(keyword));
  }

  /** 등록과 같은 값으로 통째로 바꾼다. */
  @Transactional
  public void update(Long anniversaryId, Long userId, AnniversaryRequest request) {
    Anniversary anniversary = getMine(anniversaryId, userId);

    anniversary.update(
        request.title(),
        request.anniversaryDate(),
        request.repeatType(),
        request.memo(),
        request.notificationEnabled(),
        request.notifyTime(),
        request.remindDaysBefore());
  }

  /** 기념일을 참조하는 외래키가 없어 행을 바로 지운다. */
  @Transactional
  public void delete(Long anniversaryId, Long userId) {
    anniversaryRepository.delete(getMine(anniversaryId, userId));
  }
}
