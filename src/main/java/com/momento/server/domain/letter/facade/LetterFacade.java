package com.momento.server.domain.letter.facade;

import com.momento.server.domain.image.service.GeneratedImageService;
import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.domain.letter.service.LetterDetail;
import com.momento.server.domain.letter.service.LetterService;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LetterFacade {
  private final LetterService letterService;
  private final GeneratedImageService generatedImageService;
  private final PresignedUrlProvider presignedUrlProvider;

  public void create(Long capsuleId, Long userId, LetterCreateRequest request) {
    letterService.create(capsuleId, userId, request);
  }

  public LetterResponse getMine(Long capsuleId, Long userId) {
    LetterDetail detail = letterService.getMine(capsuleId, userId);
    Long letterId = detail.letter().getId();
    return LetterResponse.from(
        detail,
        generatedImageService.findByLetterIds(List.of(letterId)).get(letterId),
        presignedUrlProvider::viewUrl);
  }

  public void delete(Long letterId, Long userId) {
    letterService.delete(letterId, userId);
  }
}
