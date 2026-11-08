package com.momento.server.domain.letter.facade;

import com.momento.server.domain.image.service.GeneratedImageService;
import com.momento.server.domain.letter.dto.response.PublicLetterListResponse;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.service.PublicLetterService;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicLetterFacade {
  private final PublicLetterService publicLetterService;
  private final GeneratedImageService generatedImageService;
  private final PresignedUrlProvider presignedUrlProvider;

  public PublicLetterListResponse getLetters(Long capsuleId, Long userId) {
    List<Letter> letters = publicLetterService.getLetters(capsuleId, userId);
    return PublicLetterListResponse.from(
        letters,
        generatedImageService.findByLetterIds(letters.stream().map(Letter::getId).toList()),
        presignedUrlProvider::viewUrl);
  }
}
