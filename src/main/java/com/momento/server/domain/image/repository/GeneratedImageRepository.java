package com.momento.server.domain.image.repository;

import com.momento.server.domain.image.entity.GeneratedImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, Long> {

  /** 가장 오래 기다린 생성 요청 하나. AI 서버에 보낼 편지 본문을 같이 읽는다. */
  @Query(
      "select g from GeneratedImage g join fetch g.letter"
          + " where g.generationStatus = com.momento.server.domain.image.entity.GenerationStatus.PENDING"
          + " order by g.id asc limit 1")
  Optional<GeneratedImage> findOldestPending();
}
