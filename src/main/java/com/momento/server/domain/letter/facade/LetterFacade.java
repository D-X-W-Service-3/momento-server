package com.momento.server.domain.letter.facade;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.domain.letter.service.LetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LetterFacade {
  private final LetterService letterService;

  public LetterResponse create(Long capsuleId, Long userId, LetterCreateRequest request) {
    return LetterResponse.from(letterService.create(capsuleId, userId, request));
  }

  public LetterResponse getMine(Long capsuleId, Long userId) {
    return LetterResponse.from(letterService.getMine(capsuleId, userId));
  }
}
