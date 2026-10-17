package com.momento.server.domain.letter.service;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.request.LetterUpdateRequest;
import com.momento.server.domain.letter.entity.Letter;
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
    requireWritable(capsule, member, LocalDateTime.now(clock));
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(member.getUser())
            .content(request.content())
            .themeType(request.themeType())
            .build());
  }

  public Letter getMine(Long capsuleId, Long userId) {
    timeCapsuleService.getActiveCapsule(capsuleId);
    timeCapsuleService.requireActiveMember(capsuleId, userId);
    // 본인 초안/제출 편지는 캡슐 공개 여부나 마감과 무관하게 조회한다.
    return letterRepository
        .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsuleId, userId)
        .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
  }

  @Transactional
  public Letter update(Long capsuleId, Long userId, LetterUpdateRequest request) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    Letter letter = getMineForUpdate(capsuleId, userId);
    requireWritable(capsule, member, LocalDateTime.now(clock));
    String content = request.hasContent() ? request.getContent() : letter.getContent();
    String themeType = request.hasThemeType() ? request.getThemeType() : letter.getThemeType();
    if (letter.isSubmitted()) {
      requireSubmittableContent(content);
    }
    letter.update(content, themeType);
    return letter;
  }

  @Transactional
  public Letter submit(Long capsuleId, Long userId) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    Letter letter = getMineForUpdate(capsuleId, userId);
    // 성공한 제출의 재시도는 마감 후에도 최초 제출 결과를 반환한다.
    if (letter.isSubmitted()) {
      return letter;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    requireWritable(capsule, member, now);
    requireSubmittableContent(letter.getContent());
    letter.submit(now);
    return letter;
  }

  @Transactional
  public void delete(Long capsuleId, Long userId) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    Letter letter = getMineForUpdate(capsuleId, userId);
    LocalDateTime now = LocalDateTime.now(clock);
    requireWritable(capsule, member, now);
    letter.delete(now);
  }

  private Letter getMineForUpdate(Long capsuleId, Long userId) {
    return letterRepository
        .findActiveForUpdate(capsuleId, userId)
        .orElseThrow(() -> new ApiException(LetterErrorCode.LETTER_NOT_FOUND));
  }

  private void requireWritable(TimeCapsule capsule, CapsuleMember member, LocalDateTime now) {
    switch (capsule.getLetterWritingEligibility(member.getRole(), now)) {
      case NOT_ALLOWED -> throw new ApiException(LetterErrorCode.LETTER_WRITING_NOT_ALLOWED);
      case CLOSED -> throw new ApiException(LetterErrorCode.LETTER_WRITING_CLOSED);
      case ALLOWED -> {
        // 작성 가능한 경우에만 변경한다.
      }
    }
  }

  private void requireSubmittableContent(String content) {
    if (content == null || content.isBlank()) {
      throw new ApiException(LetterErrorCode.LETTER_CONTENT_REQUIRED);
    }
  }
}
