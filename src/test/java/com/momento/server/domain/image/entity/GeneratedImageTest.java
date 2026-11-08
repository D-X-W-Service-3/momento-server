package com.momento.server.domain.image.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeneratedImageTest {

  private final User author = User.builder().kakaoId("1").nickname("작성자").build();
  private final TimeCapsule capsule = TimeCapsule.builder().title("캡슐").build();
  private final Letter letter =
      Letter.builder().timeCapsule(capsule).author(author).content("편지").build();

  @Test
  @DisplayName("편지로 요청하면 캡슐 · 편지 · 작성자를 잇고 PENDING 으로 시작한다")
  void requestFor() {
    GeneratedImage image = GeneratedImage.requestFor(letter);

    assertThat(image.getTimeCapsule()).isSameAs(capsule);
    assertThat(image.getLetter()).isSameAs(letter);
    assertThat(image.getRequester()).isSameAs(author);
    assertThat(image.getGenerationStatus()).isEqualTo(GenerationStatus.PENDING);
    assertThat(image.getImageUrl()).isNull();
  }

  @Test
  @DisplayName("완료하면 key 를 남기고 COMPLETED 가 된다")
  void complete() {
    GeneratedImage image = GeneratedImage.requestFor(letter);

    image.complete("generated/31/a.jpg");

    assertThat(image.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
    assertThat(image.getImageUrl()).isEqualTo("generated/31/a.jpg");
  }

  @Test
  @DisplayName("실패하면 key 없이 FAILED 가 된다")
  void fail() {
    GeneratedImage image = GeneratedImage.requestFor(letter);

    image.fail();

    assertThat(image.getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
    assertThat(image.getImageUrl()).isNull();
  }
}
