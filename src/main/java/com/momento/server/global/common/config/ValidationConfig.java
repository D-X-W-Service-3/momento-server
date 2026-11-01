package com.momento.server.global.common.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidationConfig {

  /**
   * 요청 검증의 {@code @Future} · {@code @Past} 가 서비스와 같은 {@link Clock} 빈으로 현재 시각을 판단하게 한다. 지정하지 않으면
   * 검증기가 시스템 시계를 따로 써서, 테스트에서 시각을 고정해도 검증만 실제 시각으로 동작한다.
   */
  @Bean
  public ValidationConfigurationCustomizer clockProviderCustomizer(Clock clock) {
    return configuration -> configuration.clockProvider(() -> clock);
  }
}
