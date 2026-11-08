package com.momento.server.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.momento.server.domain.image.entity.GeneratedImage;
import com.momento.server.domain.image.entity.GenerationStatus;
import com.momento.server.domain.image.external.AiImageClient;
import com.momento.server.domain.image.external.dto.AiImageRequest;
import com.momento.server.domain.image.repository.GeneratedImageRepository;
import com.momento.server.domain.image.service.AiImageGenerator;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.storage.S3ObjectUploader;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.exception.SdkClientException;

/** AI 서버와 S3 는 목으로 대체한다. 생성 작업자는 테스트에서 스케줄러 없이 직접 부른다. */
@SpringBootTest
class AiImageGeneratorIntegrationTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.POST,
          "http://ai/generate",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Autowired private AiImageGenerator generator;
  @Autowired private GeneratedImageRepository generatedImageRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private UserRepository userRepository;
  @MockitoBean private AiImageClient aiImageClient;
  @MockitoBean private S3ObjectUploader s3ObjectUploader;

  private User author;
  private TimeCapsule capsule;
  private byte[] png;

  @BeforeEach
  void setUp() throws Exception {
    cleanUp();
    author = userRepository.save(User.builder().kakaoId("ai-author").nickname("작성자").build());
    capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(author)
                .iconType("ICE_CREAM")
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .letterDeadlineAt(LocalDateTime.now().plusDays(1))
                .openAt(LocalDateTime.now().plusDays(2))
                .build());
    png = png();
  }

  @AfterEach
  void cleanUp() {
    generatedImageRepository.deleteAllInBatch();
    letterRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("PENDING 을 오래된 순서로 하나씩 만들고, JPEG 로 S3 에 올린 뒤 key 를 남긴다")
  void generatesInOrder() {
    GeneratedImage first = pending(letter("첫 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    GeneratedImage second = pending(letter("둘째 편지", LetterType.GENERAL, LetterThemeType.JOSEON));
    given(aiImageClient.generate(any())).willReturn(png);

    generator.generatePending();

    InOrder order = inOrder(aiImageClient);
    order
        .verify(aiImageClient)
        .generate(
            new AiImageRequest(
                "첫 편지", "birthday", "pstl", String.valueOf(first.getLetter().getId())));
    order
        .verify(aiImageClient)
        .generate(
            new AiImageRequest(
                "둘째 편지", "anniversary", "josen", String.valueOf(second.getLetter().getId())));

    ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
    verify(s3ObjectUploader, times(2)).upload(anyString(), content.capture(), eq("image/jpeg"));
    assertThat(content.getValue()).startsWith(0xFF, 0xD8);

    GeneratedImage done = reload(first);
    assertThat(done.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
    assertThat(done.getImageUrl()).startsWith("generated/" + first.getLetter().getId() + "/");
    assertThat(reload(second).getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
  }

  @Test
  @DisplayName("AI 서버가 400 · 500 을 주면 그 편지는 FAILED 로 두고 다음 편지로 넘어간다")
  void serverErrorFailsAndContinues() {
    GeneratedImage broken = pending(letter("깨진 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    GeneratedImage next = pending(letter("다음 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    given(aiImageClient.generate(any())).willThrow(errorStatus(500)).willReturn(png);

    generator.generatePending();

    assertThat(reload(broken).getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
    assertThat(reload(broken).getImageUrl()).isNull();
    assertThat(reload(next).getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
  }

  @Test
  @DisplayName("AI 서버가 이미지가 아닌 응답을 주면 FAILED 로 두고 넘어간다")
  void nonImageResponseFails() {
    GeneratedImage image = pending(letter("편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    given(aiImageClient.generate(any())).willReturn("{}".getBytes(StandardCharsets.UTF_8));

    generator.generatePending();

    assertThat(reload(image).getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
    verify(s3ObjectUploader, never()).upload(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("AI 서버에 연결하지 못하면 PENDING 으로 두고 이번 주기를 멈춘다")
  void connectionFailureKeepsPendingAndStops() {
    GeneratedImage first = pending(letter("첫 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    GeneratedImage second = pending(letter("둘째 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    given(aiImageClient.generate(any()))
        .willThrow(
            new RetryableException(
                -1,
                "Connection refused",
                Request.HttpMethod.POST,
                new ConnectException("Connection refused"),
                (Long) null,
                REQUEST));

    generator.generatePending();

    verify(aiImageClient, times(1)).generate(any());
    assertThat(reload(first).getGenerationStatus()).isEqualTo(GenerationStatus.PENDING);
    assertThat(reload(second).getGenerationStatus()).isEqualTo(GenerationStatus.PENDING);
  }

  @Test
  @DisplayName("S3 에 올리지 못하면 PENDING 으로 두고 이번 주기를 멈춘다")
  void uploadFailureKeepsPendingAndStops() {
    GeneratedImage first = pending(letter("첫 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    pending(letter("둘째 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    given(aiImageClient.generate(any())).willReturn(png);
    willThrow(SdkClientException.create("S3 unavailable"))
        .given(s3ObjectUploader)
        .upload(anyString(), any(), anyString());

    generator.generatePending();

    verify(aiImageClient, times(1)).generate(any());
    assertThat(reload(first).getGenerationStatus()).isEqualTo(GenerationStatus.PENDING);
    assertThat(reload(first).getImageUrl()).isNull();
  }

  @Test
  @DisplayName("기다리는 동안 삭제된 편지는 AI 를 부르지 않고 FAILED 로 둔다")
  void deletedLetterIsSkipped() {
    Letter deleted = letter("지운 편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL);
    GeneratedImage image = pending(deleted);
    deleted.delete(LocalDateTime.now());
    letterRepository.save(deleted);

    generator.generatePending();

    verify(aiImageClient, never()).generate(any());
    assertThat(reload(image).getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
  }

  @Test
  @DisplayName("주기 호출은 생성을 기다리지 않고 돌아오고, 생성 중에 다시 불려도 겹쳐 돌지 않는다")
  void triggerDoesNotBlockOrOverlap() throws Exception {
    pending(letter("편지", LetterType.BIRTHDAY, LetterThemeType.PASTEL));
    CountDownLatch called = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    given(aiImageClient.generate(any()))
        .willAnswer(
            invocation -> {
              called.countDown();
              release.await(10, TimeUnit.SECONDS);
              return png;
            });

    generator.trigger();
    assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
    generator.trigger();
    release.countDown();

    verify(s3ObjectUploader, timeout(5_000)).upload(anyString(), any(), anyString());
    verify(aiImageClient, times(1)).generate(any());
  }

  private Letter letter(String content, LetterType letterType, LetterThemeType themeType) {
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .title("제목")
            .content(content)
            .letterType(letterType)
            .themeType(themeType)
            .status(LetterStatus.SUBMITTED)
            .submittedAt(LocalDateTime.now())
            .build());
  }

  private GeneratedImage pending(Letter letter) {
    return generatedImageRepository.save(GeneratedImage.requestFor(letter));
  }

  private GeneratedImage reload(GeneratedImage image) {
    return generatedImageRepository.findById(image.getId()).orElseThrow();
  }

  private static FeignException errorStatus(int status) {
    return FeignException.errorStatus(
        "AiImageClient#generate",
        Response.builder()
            .status(status)
            .reason("error")
            .request(REQUEST)
            .headers(Map.of())
            .body("{\"detail\":\"생성 실패\"}", StandardCharsets.UTF_8)
            .build());
  }

  private static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}
