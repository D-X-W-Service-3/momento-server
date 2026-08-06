package com.momento.server.global.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestApiController {

  @AliasFor(annotation = RequestMapping.class, attribute = "value")
  String value() default "";
}
