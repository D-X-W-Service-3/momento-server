package com.momento.server.global.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * 스케줄링이 테스트에서는 꺼지고, 설정이 없으면 켜지는지 검증한다.
 *
 * <p>스케줄링을 꺼도 {@code @Scheduled} 메서드를 가진 빈은 남고 실행만 안 된다. 그래서 스케줄러 빈이 아니라 {@code @Scheduled} 를 처리하는
 * {@link ScheduledAnnotationBeanPostProcessor} 가 있는지로 판단한다.
 */
@SpringBootTest
class SchedulingConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("테스트 설정으로 띄운 컨텍스트에서는 스케줄링이 꺼져 있다")
  void disabledInTests() {
    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
  }

  @Test
  @DisplayName("설정이 없으면 스케줄링이 켜진다")
  void enabledByDefault() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  @DisplayName("false 로 두면 스케줄링이 꺼진다")
  void disabledByProperty() {
    runner
        .withPropertyValues("momento.scheduling.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
  }
}
