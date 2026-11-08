package com.momento.server.domain.letter.dto.response;

import com.momento.server.domain.image.entity.GeneratedImage;
import com.momento.server.domain.image.entity.GenerationStatus;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;
import com.momento.server.domain.letter.service.LetterDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.function.Function;

@Schema(description = "내 편지 응답")
public record LetterResponse(
    @Schema(description = "편지 ID", example = "31") Long letterId,
    @Schema(description = "편지 유형. 기존 편지는 null일 수 있다.", example = "BIRTHDAY", nullable = true)
        LetterType letterType,
    @Schema(description = "편지지 화풍. 기존 편지는 null일 수 있다.", example = "WATERCOLOR", nullable = true)
        LetterThemeType themeType,
    @Schema(description = "편지 제목. 기존 편지는 null일 수 있다.", nullable = true) String title,
    @Schema(description = "편지 내용") String content,
    @Schema(description = "작성 일시") LocalDateTime createdAt,
    @Schema(description = "조회 시점에 WRITING이며 작성 마감·공개 시각 전이면 true. 삭제 요청 시 다시 검사한다.")
        boolean canDelete,
    @Schema(
            description = "AI 이미지 조회용 presigned URL(1시간 유효). imageStatus 가 COMPLETED 일 때만 있다.",
            nullable = true)
        String imageUrl,
    @Schema(
            description = "AI 이미지 생성 상태. PENDING 이면 생성 중이고, 이미지 생성 대상이 아닌 예전 편지는 null 이다.",
            nullable = true)
        GenerationStatus imageStatus) {
  public static LetterResponse from(
      LetterDetail detail, GeneratedImage image, Function<String, String> viewUrlOf) {
    Letter letter = detail.letter();
    return new LetterResponse(
        letter.getId(),
        letter.getLetterType(),
        letter.getThemeType(),
        letter.getTitle(),
        letter.getContent(),
        letter.getCreatedAt(),
        detail.canDelete(),
        LetterImage.viewUrl(image, viewUrlOf),
        LetterImage.status(image));
  }
}
