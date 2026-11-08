package com.momento.server.domain.image.service;

import com.momento.server.domain.image.external.AiImageClient;
import com.momento.server.domain.image.service.GeneratedImageService.PendingImage;
import com.momento.server.global.common.storage.S3ObjectUploader;
import feign.FeignException;
import feign.RetryableException;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * PENDING 인 편지 이미지를 오래된 순서로 한 장씩 만든다. AI 서버는 GPU 가 1장이라 요청을 하나씩 처리하므로, 여러 장을 동시에 보내도 빨라지지 않고 몇 분짜리
 * 연결만 늘어난다.
 *
 * <p>생성은 전용 스레드 하나에서 돈다. 스케줄러 스레드에서 직접 돌리면 한 장(약 2분 30초) 동안 캡슐 상태 전이와 알림 스케줄러가 멈춘다(기본 스케줄러는 스레드가
 * 하나다). 이 스레드를 스프링 빈({@code TaskScheduler} · {@code Executor})으로 등록하지 않는 것도 같은 이유다. 빈으로 두면 스프링 부트가
 * 기본 스케줄러 · {@code @Async} 실행기를 만들지 않고 이 스레드를 대신 써 버린다.
 *
 * <p>실패는 원인에 따라 다르게 다룬다.
 *
 * <ul>
 *   <li>AI 서버가 응답한 오류(400 · 500)나 이미지가 아닌 응답: 그 편지는 FAILED 로 두고 다음 편지로 넘어간다.
 *   <li>연결 실패 · 타임아웃(프록시 꺼짐, 로그인 만료)과 S3 업로드 실패: PENDING 으로 두고 이번 주기를 멈춘다. 다음 주기에 같은 편지부터 다시 한다.
 * </ul>
 */
@Slf4j
@Component
public class AiImageGenerator {

  private final GeneratedImageService generatedImageService;
  private final AiImageClient aiImageClient;
  private final S3ObjectUploader s3ObjectUploader;

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "ai-image-generator");
            // 생성 중에 서버가 내려가도 종료를 막지 않는다. 처리 중이던 편지는 PENDING 으로 남아 다음 기동 때 다시 만든다.
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicBoolean running = new AtomicBoolean(false);

  public AiImageGenerator(
      GeneratedImageService generatedImageService,
      AiImageClient aiImageClient,
      S3ObjectUploader s3ObjectUploader) {
    this.generatedImageService = generatedImageService;
    this.aiImageClient = aiImageClient;
    this.s3ObjectUploader = s3ObjectUploader;
  }

  /** 생성 스레드를 깨우기만 하고 바로 돌아온다. 이미 돌고 있으면 아무것도 하지 않는다. */
  @Scheduled(fixedDelayString = "${momento.image.generation-interval:10s}")
  public void trigger() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    executor.execute(
        () -> {
          try {
            generatePending();
          } catch (RuntimeException exception) {
            log.error("AI 이미지 생성 중단. 다음 주기에 다시 시도한다.", exception);
          } finally {
            running.set(false);
          }
        });
  }

  /** 남은 요청을 오래된 순서로 하나씩 처리한다. 다시 시도해야 하는 실패가 나면 멈춘다. */
  public void generatePending() {
    Optional<PendingImage> next = generatedImageService.findOldestPending();
    while (next.isPresent() && generate(next.get())) {
      next = generatedImageService.findOldestPending();
    }
  }

  /** 한 장을 처리한다. 다음 요청으로 넘어가도 되면 true, 이번 주기를 멈춰야 하면 false. */
  private boolean generate(PendingImage pending) {
    if (pending.letterDeleted()) {
      generatedImageService.fail(pending.imageId());
      return true;
    }

    byte[] png;
    try {
      png = aiImageClient.generate(pending.request());
    } catch (RetryableException exception) {
      log.warn(
          "AI 서버에 연결하지 못했다(편지 {}). 프록시 · 로그인 상태를 확인한다. 다음 주기에 다시 시도한다.",
          pending.letterId(),
          exception);
      return false;
    } catch (FeignException exception) {
      log.warn(
          "AI 서버가 이미지를 만들지 못했다(편지 {}, 상태 {}): {}",
          pending.letterId(),
          exception.status(),
          exception.contentUTF8());
      generatedImageService.fail(pending.imageId());
      return true;
    }

    byte[] jpeg;
    try {
      jpeg = JpegEncoder.fromPng(png);
    } catch (IllegalArgumentException exception) {
      log.warn("AI 서버 응답을 이미지로 읽지 못했다(편지 {}).", pending.letterId(), exception);
      generatedImageService.fail(pending.imageId());
      return true;
    }

    String key = ImageKeys.newGeneratedKey(pending.letterId());
    try {
      s3ObjectUploader.upload(key, jpeg, JpegEncoder.CONTENT_TYPE);
    } catch (SdkException exception) {
      log.warn("AI 이미지를 S3 에 올리지 못했다(편지 {}). 다음 주기에 다시 시도한다.", pending.letterId(), exception);
      return false;
    }
    generatedImageService.complete(pending.imageId(), key);
    return true;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
