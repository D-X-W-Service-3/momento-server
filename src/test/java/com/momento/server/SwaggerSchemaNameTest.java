package com.momento.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Swagger 스키마 이름이 DTO 마다 다른지 검증한다.
 *
 * <p>springdoc 은 스키마 이름을 클래스의 단순 이름(또는 {@code @Schema(name)})으로 짓고, 이름이 같으면 경고 없이 하나로 합친다. 그러면 안쪽
 * record 가 모두 {@code Item} 인 응답들이 문서에서 한 스키마를 가리킨다. 겹치면 {@code @Schema(name = "...")} 로 이름을 붙인다.
 */
class SwaggerSchemaNameTest {

  @Test
  @DisplayName("요청 · 응답 DTO 의 스키마 이름이 겹치지 않는다")
  void schemaNamesAreUnique() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(
        (reader, factory) -> reader.getClassMetadata().getClassName().contains(".dto."));

    Map<String, List<String>> classesBySchemaName =
        scanner.findCandidateComponents("com.momento.server").stream()
            .map(BeanDefinition::getBeanClassName)
            .map(name -> ClassUtils.resolveClassName(name, getClass().getClassLoader()))
            .collect(
                Collectors.groupingBy(
                    SwaggerSchemaNameTest::schemaName,
                    Collectors.mapping(Class::getName, Collectors.toList())));

    assertThat(classesBySchemaName).isNotEmpty();
    assertThat(classesBySchemaName)
        .allSatisfy((name, classes) -> assertThat(classes).as(name).hasSize(1));
  }

  private static String schemaName(Class<?> type) {
    Schema schema = type.getAnnotation(Schema.class);
    if (schema != null && !schema.name().isEmpty()) {
      return schema.name();
    }
    return type.getSimpleName();
  }
}
