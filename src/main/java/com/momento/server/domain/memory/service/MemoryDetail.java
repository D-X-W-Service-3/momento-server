package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import java.util.List;

/** 아카이브 상세의 서비스 결과. 사진은 display_order 오름차순이다. 응답 모양으로 바꾸는 일은 Facade 가 맡는다. */
public record MemoryDetail(Memory memory, List<MemoryImage> images) {}
