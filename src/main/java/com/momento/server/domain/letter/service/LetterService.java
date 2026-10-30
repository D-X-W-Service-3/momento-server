package com.momento.server.domain.letter.service;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.exception.LetterErrorCode;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterService {
  private final LetterRepository letterRepository;
  private final TimeCapsuleService timeCapsuleService;
  private final Clock clock;

  @Transactional
  public Letter create(Long capsuleId, Long userId, LetterCreateRequest request) {
    // 편지가 아직 없는 경우에도 잠글 수 있는 부모 행을 사용한다. JVM 안의 synchronized는 여러 서버를 보호하지 못한다.
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    if (letterRepository.findActiveForUpdate(capsuleId, userId).isPresent()) {
      throw new ApiException(LetterErrorCode.LETTER_ALREADY_EXISTS);
    }
    // 잠금 대기 중 마감될 수 있으므로 시간은 잠금을 얻은 뒤 읽는다.
    LocalDateTime now = LocalDateTime.now(clock);
    switch (capsule.getLetterWritingEligibility(member.getRole(), now)) {
      case NOT_ALLOWED -> throw new ApiException(LetterErrorCode.LETTER_WRITING_NOT_ALLOWED);
      case CLOSED -> throw new ApiException(LetterErrorCode.LETTER_WRITING_CLOSED);
      case ALLOWED -> {
        // 작성 가능한 경우에만 아래 저장을 진행한다.
      }
    }
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(member.getUser())
            .title(request.title())
            .content(request.content())
            .themeType(request.themeType())
            .status(LetterStatus.SUBMITTED)
            .submittedAt(now)
            .build());
  }

  /**
   * 캡슐 ID만 먼저 읽고 캡슐 → 편지 순서로 잠근다. 초기 조회로 편지를 캐시에 올리지 않는다. READ_COMMITTED는 잠금을 기다리는 동안 내보내진 멤버의 상태도
   * 최신 값으로 읽기 위해 필요하다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(Long letterId, Long userId) {
    Long capsuleId =
        letterRepository
            .findCapsuleIdForDeletion(letterId, userId)
            .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    timeCapsuleService.requireActiveMember(capsuleId, userId);
    Letter letter =
        letterRepository
            .findActiveByIdForUpdate(letterId, userId, capsuleId)
            .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    if (!capsule.isLetterWritingOpen(now)) {
      throw new ApiException(LetterErrorCode.LETTER_WRITING_CLOSED);
    }
    letter.delete(now);
  }

  public LetterDetail getMine(Long capsuleId, Long userId) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsule(capsuleId);
    timeCapsuleService.requireActiveMember(capsuleId, userId);
    // 본인 초안/제출 편지는 캡슐 공개 여부나 마감과 무관하게 조회한다.
    Letter letter =
        letterRepository
            .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsuleId, userId)
            .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
    return new LetterDetail(letter, capsule.isLetterWritingOpen(LocalDateTime.now(clock)));
  }
}
