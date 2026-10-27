package com.momento.server.global.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 실행을 켠다. 저장소에 스케줄러가 처음 들어오는 지점이다.
 *
 * <p>운영 서버를 한 대로 두는 것을 전제로 한다. 여러 대로 늘리면 같은 작업이 동시에 돌 수 있으므로, 각 스케줄러가 조건을 UPDATE 에 함께 넣어 중복 처리를 막거나
 * 분산 잠금을 도입해야 한다.
 *
 * <p>테스트에서는 {@code momento.scheduling.enabled=false} 로 끈다. 테스트 컨텍스트들이 H2 하나를 같이 써서, 켜 두면 한 컨텍스트의
 * 스케줄러가 다른 테스트가 만든 캡슐 상태를 바꾼다. 값이 없으면 켜진다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "momento.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {}
