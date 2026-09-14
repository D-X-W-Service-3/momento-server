package com.momento.server.domain.letter.service;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.exception.LetterErrorCode;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterService {
  private final LetterRepository letterRepository;
  private final TimeCapsuleRepository timeCapsuleRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final Clock clock;

  @Transactional
  public LetterResponse create(Long capsuleId, Long userId, LetterCreateRequest request) {
    // 편지가 아직 없는 경우에도 잠글 수 있는 부모 행을 사용한다. JVM 안의 synchronized는 여러 서버를 보호하지 못한다.
    TimeCapsule capsule =
        timeCapsuleRepository
            .findActiveByIdForUpdate(capsuleId)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
    CapsuleMember member = requireActiveMember(capsuleId, userId);
    if (letterRepository.findActiveForUpdate(capsuleId, userId).isPresent()) {
      throw new ApiException(LetterErrorCode.LETTER_ALREADY_EXISTS);
    }
    // 잠금 대기 중 마감될 수 있으므로 시간은 잠금을 얻은 뒤 읽는다.
    if (!capsule.canWriteLetter(LocalDateTime.now(clock))) {
      throw new ApiException(LetterErrorCode.LETTER_WRITING_CLOSED);
    }
    Letter letter =
        letterRepository.save(
            Letter.builder()
                .timeCapsule(capsule)
                .author(member.getUser())
                .content(request.content())
                .themeType(request.themeType())
                .build());
    return LetterResponse.from(letter);
  }

  public LetterResponse getMine(Long capsuleId, Long userId) {
    timeCapsuleRepository
        .findByIdAndDeletedAtIsNull(capsuleId)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
    requireActiveMember(capsuleId, userId);
    // 본인 초안/제출 편지는 캡슐 공개 여부나 마감과 무관하게 조회한다.
    return letterRepository
        .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsuleId, userId)
        .map(LetterResponse::from)
        .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
  }

  private CapsuleMember requireActiveMember(Long capsuleId, Long userId) {
    return capsuleMemberRepository
        .findByTimeCapsuleIdAndUserIdAndStatus(capsuleId, userId, MemberStatus.ACTIVE)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }
}
