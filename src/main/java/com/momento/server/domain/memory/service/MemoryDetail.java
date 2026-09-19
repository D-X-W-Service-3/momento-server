package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import java.util.List;

/** 추억 등록의 서비스 결과. 서비스는 웹 응답 DTO 대신 이 결과를 돌려주고, 응답 모양으로 바꾸는 일은 Facade 가 맡는다. */
public record MemoryDetail(Memory memory, List<MemoryImage> images) {}
