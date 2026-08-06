package com.momento.server.global.common.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** 외부 AI 서버 등 OpenFeign 클라이언트 스캔 설정. FeignClient 인터페이스는 domain 하위(예: domain/ai/external)에 위치시킨다. */
@Configuration
@EnableFeignClients(basePackages = "com.momento.server.domain")
public class FeignClientConfig {}
