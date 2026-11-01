package com.momento.server.global.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info =
        @Info(
            title = "Momento API",
            description = "소중한 순간을 오래도록 기억하는 타임캡슐·추억 아카이빙 서비스",
            version = "v1"))
@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    SecurityRequirement securityRequirement = new SecurityRequirement().addList("JWT");
    Components components =
        new Components()
            .addSecuritySchemes(
                "JWT",
                new SecurityScheme()
                    .name("JWT")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
    // 상대 경로라 Swagger 를 연 서버(로컬 · 운영)로 요청이 간다
    return new OpenAPI()
        .servers(List.of(new Server().url("/").description("Current")))
        .addSecurityItem(securityRequirement)
        .components(components);
  }
}
