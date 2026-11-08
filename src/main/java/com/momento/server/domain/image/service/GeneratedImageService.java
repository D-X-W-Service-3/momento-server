package com.momento.server.domain.image.service;

import com.momento.server.domain.image.entity.GeneratedImage;
import com.momento.server.domain.image.external.dto.AiImageRequest;
import com.momento.server.domain.image.repository.GeneratedImageRepository;
import com.momento.server.domain.letter.entity.Letter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편지 AI 이미지의 생성 요청과 상태. 대기열은 따로 두지 않고 {@code generation_status = PENDING} 인 행을 그대로 쓴다. 배포로 서버가 다시
 * 떠도 남은 요청부터 이어서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class GeneratedImageService {

  private final GeneratedImageRepository generatedImageRepository;

  /**
   * 편지를 저장하는 트랜잭션 안에서 생성 요청을 남긴다. 편지와 요청이 함께 저장되거나 함께 취소된다. 화풍이 없는 예전 편지는 AI 화풍을 정할 수 없어 요청하지 않는다.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void requestFor(Letter letter) {
    if (letter.getThemeType() == null) {
      return;
    }
    generatedImageRepository.save(GeneratedImage.requestFor(letter));
  }

  /** 가장 오래 기다린 요청. 편지가 그 사이 삭제됐으면 {@code letterDeleted} 가 true 다. */
  @Transactional
  public Optional<PendingImage> findOldestPending() {
    return generatedImageRepository
        .findOldestPending()
        .map(
            image -> {
              Letter letter = image.getLetter();
              return new PendingImage(
                  image.getId(),
                  letter.getId(),
                  letter.getDeletedAt() != null,
                  AiImageRequest.of(
                      letter.getId(),
                      letter.getContent(),
                      letter.getLetterType(),
                      letter.getThemeType()));
            });
  }

  @Transactional
  public void complete(Long imageId, String imageKey) {
    generatedImageRepository.findById(imageId).ifPresent(image -> image.complete(imageKey));
  }

  @Transactional
  public void fail(Long imageId) {
    generatedImageRepository.findById(imageId).ifPresent(GeneratedImage::fail);
  }

  /** AI 서버에 보낼 요청 하나. 엔티티 대신 값만 넘겨, 몇 분 걸리는 호출 동안 영속성 컨텍스트를 붙잡지 않는다. */
  public record PendingImage(
      Long imageId, Long letterId, boolean letterDeleted, AiImageRequest request) {}
}
