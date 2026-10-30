package com.momento.server.domain.memory;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 아카이브 상세 조회 · 수정 · 삭제 API 를 실제 시큐리티 필터까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemoryManageIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private MemoryImageRepository memoryImageRepository;
  @Autowired private TokenProvider tokenProvider;

  private User me;
  private User other;
  private String token;

  @BeforeEach
  void setUp() {
    clearAll();
    me = userRepository.save(User.builder().kakaoId("kakao-me").nickname("서현").build());
    other = userRepository.save(User.builder().kakaoId("kakao-other").nickname("남").build());
    token = tokenProvider.generateToken(me, Duration.ofMinutes(10));
  }

  @AfterEach
  void tearDown() {
    clearAll();
  }

  private void clearAll() {
    memoryImageRepository.deleteAll();
    memoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private static String imageKey(User owner) {
    return "memories/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg";
  }

  private Memory saveMemory(User owner, String title) {
    return memoryRepository.save(
        Memory.builder()
            .user(owner)
            .title(title)
            .content("내용")
            .memoryDate(LocalDate.of(2026, 8, 15))
            .build());
  }

  private Memory saveDeletedMemory(User owner) {
    Memory memory = saveMemory(owner, "지운 추억");
    memory.delete(LocalDateTime.of(2026, 10, 1, 0, 0));
    return memoryRepository.save(memory);
  }

  private void saveImage(Memory memory, String key, int order) {
    memoryImageRepository.save(
        MemoryImage.builder().memory(memory).imageUrl(key).displayOrder(order).build());
  }

  private ResultActions getMemory(Long memoryId) throws Exception {
    return mockMvc.perform(
        get("/v1/memories/{memoryId}", memoryId).header("Authorization", "Bearer " + token));
  }

  @Test
  @DisplayName("상세는 내 아카이브를 사진 전체와 함께 표시 순서대로 주고, 첫 사진이 대표 사진이다")
  void getDetail() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String second = imageKey(me);
    String first = imageKey(me);
    saveImage(memory, second, 1);
    saveImage(memory, first, 0);

    getMemory(memory.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memoryId").value(memory.getId()))
        .andExpect(jsonPath("$.data.title").value("제주도 여행"))
        .andExpect(jsonPath("$.data.content").value("내용"))
        .andExpect(jsonPath("$.data.memoryDate").value("2026-08-15"))
        .andExpect(jsonPath("$.data.authorNickname").value("서현"))
        .andExpect(jsonPath("$.data.isMine").value(true))
        .andExpect(jsonPath("$.data.createdAt").isString())
        .andExpect(jsonPath("$.data.images.length()").value(2))
        .andExpect(jsonPath("$.data.images[0].imageId").isNumber())
        .andExpect(jsonPath("$.data.images[0].imageKey").value(first))
        .andExpect(jsonPath("$.data.images[0].displayOrder").value(0))
        .andExpect(jsonPath("$.data.images[0].isRepresentative").value(true))
        .andExpect(jsonPath("$.data.images[0].imageUrl").value(containsString("/" + first + "?")))
        .andExpect(jsonPath("$.data.images[0].imageUrl").value(containsString("X-Amz-Signature=")))
        .andExpect(jsonPath("$.data.images[1].imageKey").value(second))
        .andExpect(jsonPath("$.data.images[1].isRepresentative").value(false));
  }

  @Test
  @DisplayName("사진이 없으면 images 는 빈 목록이다")
  void detailWithoutImages() throws Exception {
    Memory memory = saveMemory(me, "사진 없음");

    getMemory(memory.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.images").isEmpty());
  }

  @Test
  @DisplayName("없는 아카이브 · 삭제된 아카이브 · 다른 회원의 아카이브는 똑같이 404 MEMORY_NOT_FOUND 다")
  void detailNotFound() throws Exception {
    Memory deleted = saveDeletedMemory(me);
    Memory others = saveMemory(other, "남의 추억");

    for (Long memoryId : List.of(deleted.getId(), others.getId(), others.getId() + 1000)) {
      getMemory(memoryId)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    }
  }

  @Test
  @DisplayName("토큰 없이 상세를 조회하면 401 이다")
  void detailRequiresAuthentication() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");

    mockMvc
        .perform(get("/v1/memories/{memoryId}", memory.getId()))
        .andExpect(status().isUnauthorized());
  }
}
