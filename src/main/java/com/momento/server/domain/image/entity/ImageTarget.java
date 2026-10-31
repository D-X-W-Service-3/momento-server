package com.momento.server.domain.image.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 업로드하는 이미지의 용도. 용도마다 S3 key 의 첫 경로가 다르다. */
@Getter
@RequiredArgsConstructor
public enum ImageTarget {
  MEMORY("memories"),
  PROFILE("profiles"),
  ;

  private final String directory;
}
