package com.momento.server.domain.letter.service;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.global.common.exception.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicLetterService {
  private final TimeCapsuleService timeCapsuleService;
  private final LetterRepository letterRepository;

  public List<Letter> getLetters(Long capsuleId, Long userId) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsule(capsuleId);
    // 외부인에게 공개 여부를 노출하지 않도록 참여 확인을 먼저 수행한다.
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    if (!capsule.canViewLetters(member.getRole())) {
      throw new ApiException(CapsuleErrorCode.CAPSULE_NOT_OPENED);
    }
    boolean viewAll = capsule.canViewAllLetters(member.getRole());
    return letterRepository.findVisibleSubmittedLetters(capsuleId, userId, viewAll);
  }
}
