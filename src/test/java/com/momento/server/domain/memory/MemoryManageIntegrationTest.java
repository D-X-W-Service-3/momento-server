package com.momento.server.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
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
    timeCapsuleRepository.deleteAll();
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

  /** 타임캡슐에서 옮겨진 내 추억. 목록에 보이지 않는다. */
  private Memory saveMovedFromCapsule(User owner) {
    LocalDateTime openAt = LocalDateTime.of(2045, 1, 1, 0, 0);
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(CapsuleStatus.WRITING)
                .letterDeadlineAt(openAt.minusDays(10))
                .openAt(openAt)
                .build());
    return memoryRepository.save(
        Memory.builder()
            .user(owner)
            .timeCapsule(capsule)
            .title("옮긴 추억")
            .memoryDate(LocalDate.of(2026, 8, 15))
            .build());
  }

  private MemoryImage saveImage(Memory memory, String key, int order) {
    return memoryImageRepository.save(
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

  private ResultActions updateMemory(Long memoryId, String body) throws Exception {
    return mockMvc.perform(
        patch("/v1/memories/{memoryId}", memoryId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private List<Long> imageIdsOf(Memory memory) {
    return memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(memory.getId()).stream()
        .map(MemoryImage::getId)
        .toList();
  }

  private List<String> imageKeysOf(Memory memory) {
    return memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(memory.getId()).stream()
        .map(MemoryImage::getImageUrl)
        .toList();
  }

  private Memory reload(Memory memory) {
    return memoryRepository.findById(memory.getId()).orElseThrow();
  }

  @Test
  @DisplayName("보낸 값만 바꾸고 보내지 않은 값과 사진은 그대로 둔다")
  void updateOnlyGivenValues() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String key = imageKey(me);
    saveImage(memory, key, 0);

    updateMemory(memory.getId(), "{\"title\": \"부산 여행\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());

    Memory updated = reload(memory);
    assertThat(updated.getTitle()).isEqualTo("부산 여행");
    assertThat(updated.getContent()).isEqualTo("내용");
    assertThat(updated.getMemoryDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(imageKeysOf(memory)).containsExactly(key);
  }

  @Test
  @DisplayName("imageKeys 를 보내면 빠진 사진은 지우고 새 사진을 더하고, 대표 사진은 첫 번째로 옮기며, 남은 사진의 imageId 는 그대로다")
  void replaceImages() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String removed = imageKey(me);
    String kept = imageKey(me);
    String added = imageKey(me);
    saveImage(memory, removed, 0);
    MemoryImage keptImage = saveImage(memory, kept, 1);

    updateMemory(
            memory.getId(),
            """
            {"imageKeys": ["%s", "%s"], "representativeImageKey": "%s"}
            """
                .formatted(kept, added, added))
        .andExpect(status().isOk());

    assertThat(imageKeysOf(memory)).containsExactly(added, kept);
    assertThat(imageIdsOf(memory).get(1)).isEqualTo(keptImage.getId());
    assertThat(reload(memory).getTitle()).isEqualTo("제주도 여행");
  }

  @Test
  @DisplayName("빈 imageKeys 를 보내면 사진이 모두 빠진다")
  void removeAllImages() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    saveImage(memory, imageKey(me), 0);

    updateMemory(memory.getId(), "{\"imageKeys\": []}").andExpect(status().isOk());

    assertThat(imageKeysOf(memory)).isEmpty();
  }

  @Test
  @DisplayName("대표 사진 key 만 보내면 기존 사진 중 그 사진을 첫 번째로 옮기고 imageId 는 그대로다")
  void changeRepresentativeOnly() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String first = imageKey(me);
    String second = imageKey(me);
    String third = imageKey(me);
    Long firstId = saveImage(memory, first, 0).getId();
    Long secondId = saveImage(memory, second, 1).getId();
    Long thirdId = saveImage(memory, third, 2).getId();

    updateMemory(memory.getId(), "{\"representativeImageKey\": \"%s\"}".formatted(third))
        .andExpect(status().isOk());

    assertThat(imageKeysOf(memory)).containsExactly(third, first, second);
    assertThat(imageIdsOf(memory)).containsExactly(thirdId, firstId, secondId);
  }

  @Test
  @DisplayName("순서만 바꾸면 모든 사진의 imageId 가 그대로다")
  void reorderKeepsImageIds() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String first = imageKey(me);
    String second = imageKey(me);
    Long firstId = saveImage(memory, first, 0).getId();
    Long secondId = saveImage(memory, second, 1).getId();

    updateMemory(memory.getId(), "{\"imageKeys\": [\"%s\", \"%s\"]}".formatted(second, first))
        .andExpect(status().isOk());

    assertThat(imageKeysOf(memory)).containsExactly(second, first);
    assertThat(imageIdsOf(memory)).containsExactly(secondId, firstId);
  }

  @Test
  @DisplayName("다른 회원의 key · 목록에 없는 대표 사진이면 400 INVALID_IMAGE_KEY 이고 아무것도 바뀌지 않는다")
  void updateRejectsInvalidImageKeys() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String mine = imageKey(me);
    saveImage(memory, mine, 0);

    for (String body :
        List.of(
            "{\"title\": \"바뀐 이름\", \"imageKeys\": [\"%s\"]}".formatted(imageKey(other)),
            "{\"title\": \"바뀐 이름\", \"representativeImageKey\": \"%s\"}".formatted(imageKey(me)))) {
      updateMemory(memory.getId(), body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_IMAGE_KEY"));
    }

    assertThat(reload(memory).getTitle()).isEqualTo("제주도 여행");
    assertThat(imageKeysOf(memory)).containsExactly(mine);
  }

  @Test
  @DisplayName("공백 이름 · 15자 넘는 이름 · 45자 넘는 내용 · 9장 사진은 400 INVALID_INPUT_VALUE 다")
  void updateValidatesBody() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    String nine =
        IntStream.rangeClosed(1, 9)
            .mapToObj(i -> "\"" + imageKey(me) + "\"")
            .collect(Collectors.joining(","));

    for (String body :
        List.of(
            "{\"title\": \" \"}",
            "{\"title\": \"" + "가".repeat(16) + "\"}",
            "{\"content\": \"" + "가".repeat(46) + "\"}",
            "{\"imageKeys\": [" + nine + "]}")) {
      updateMemory(memory.getId(), body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    assertThat(reload(memory).getTitle()).isEqualTo("제주도 여행");
  }

  @Test
  @DisplayName("삭제된 아카이브 · 다른 회원의 아카이브는 수정할 수 없고 404 MEMORY_NOT_FOUND 다")
  void updateNotFound() throws Exception {
    Memory deleted = saveDeletedMemory(me);
    Memory others = saveMemory(other, "남의 추억");

    for (Memory memory : List.of(deleted, others)) {
      updateMemory(memory.getId(), "{\"title\": \"바뀐 이름\"}")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    }

    assertThat(reload(others).getTitle()).isEqualTo("남의 추억");
  }

  private ResultActions deleteMemory(Long memoryId) throws Exception {
    return mockMvc.perform(
        delete("/v1/memories/{memoryId}", memoryId).header("Authorization", "Bearer " + token));
  }

  @Test
  @DisplayName("삭제하면 data 없는 200 이고, 삭제 시각이 채워지며 상세 · 목록에서 사라진다")
  void deletesMemory() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");
    saveImage(memory, imageKey(me), 0);
    saveMemory(me, "남길 추억");

    deleteMemory(memory.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());

    assertThat(reload(memory).getDeletedAt()).isNotNull();
    getMemory(memory.getId()).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/v1/memories").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.memories[0].title").value("남길 추억"));
  }

  @Test
  @DisplayName("없는 아카이브 · 이미 삭제된 아카이브 · 다른 회원의 아카이브는 지울 수 없고 404 MEMORY_NOT_FOUND 다")
  void deleteNotFound() throws Exception {
    Memory deleted = saveDeletedMemory(me);
    Memory others = saveMemory(other, "남의 추억");

    for (Long memoryId : List.of(deleted.getId(), others.getId(), others.getId() + 1000)) {
      deleteMemory(memoryId)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    }

    assertThat(reload(others).getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("토큰 없이 수정 · 삭제하면 401 이고 아무것도 바뀌지 않는다")
  void manageRequiresAuthentication() throws Exception {
    Memory memory = saveMemory(me, "제주도 여행");

    mockMvc
        .perform(
            patch("/v1/memories/{memoryId}", memory.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"바뀐 이름\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(delete("/v1/memories/{memoryId}", memory.getId()))
        .andExpect(status().isUnauthorized());

    Memory unchanged = reload(memory);
    assertThat(unchanged.getTitle()).isEqualTo("제주도 여행");
    assertThat(unchanged.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("타임캡슐에서 옮겨진 추억은 목록처럼 상세 · 수정 · 삭제에서도 404 MEMORY_NOT_FOUND 이고 바뀌지 않는다")
  void movedFromCapsuleIsNotFound() throws Exception {
    Memory moved = saveMovedFromCapsule(me);

    getMemory(moved.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    updateMemory(moved.getId(), "{\"title\": \"바뀐 이름\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    deleteMemory(moved.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));

    Memory unchanged = reload(moved);
    assertThat(unchanged.getTitle()).isEqualTo("옮긴 추억");
    assertThat(unchanged.getDeletedAt()).isNull();
  }
}
