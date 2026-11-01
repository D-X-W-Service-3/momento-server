package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import java.util.List;
import java.util.Map;

/**
 * 받은 아카이브와 원본 추억의 사진.
 *
 * @param imagesByMemory 추억 ID 별 사진. display_order 오름차순
 */
public record ReceivedMemories(
    List<ReceivedMemory> received, Map<Long, List<MemoryImage>> imagesByMemory) {}
