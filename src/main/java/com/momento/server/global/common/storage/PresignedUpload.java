package com.momento.server.global.common.storage;

import java.time.LocalDateTime;

/** 업로드용 presigned URL 과 그 만료 시각. */
public record PresignedUpload(String url, LocalDateTime expiresAt) {}
