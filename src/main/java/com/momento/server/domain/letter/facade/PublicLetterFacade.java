package com.momento.server.domain.letter.facade;

import com.momento.server.domain.letter.dto.response.PublicLetterListResponse;
import com.momento.server.domain.letter.service.PublicLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicLetterFacade {
  private final PublicLetterService publicLetterService;

  public PublicLetterListResponse getLetters(Long capsuleId, Long userId) {
    return PublicLetterListResponse.from(publicLetterService.getLetters(capsuleId, userId));
  }
}
