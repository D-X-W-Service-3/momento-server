package com.momento.server.domain.anniversary.service;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.exception.AnniversaryErrorCode;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.exception.ApiException;
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

  /** 등록과 같은 값으로 통째로 바꾼다. */
  @Transactional
  public Anniversary update(Long anniversaryId, Long userId, AnniversaryRequest request) {
    Anniversary anniversary = getMine(anniversaryId, userId);

    anniversary.update(
        request.title(),
        request.anniversaryDate(),
        request.repeatType(),
        request.memo(),
        request.notificationEnabled(),
        request.notifyTime(),
        request.remindDaysBefore());

    return anniversary;
  }

  /** 기념일을 참조하는 외래키가 없어 행을 바로 지운다. */
  @Transactional
  public void delete(Long anniversaryId, Long userId) {
    anniversaryRepository.delete(getMine(anniversaryId, userId));
  }
}
