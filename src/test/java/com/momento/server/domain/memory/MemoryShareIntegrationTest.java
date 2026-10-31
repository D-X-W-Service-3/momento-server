package com.momento.server.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryVisibilityType;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import com.momento.server.domain.memory.entity.ReceivedMemoryStatus;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.memory.repository.ReceivedMemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 아카이브 공유 링크 발급 · 링크로 받기 API 를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemoryShareIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private MemoryImageRepository memoryImageRepository;
  @Autowired private ReceivedMemoryRepository receivedMemoryRepository;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("share-me", "나");
    friend = saveUser("share-friend", "친구");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("내 아카이브를 공유하면 토큰을 주고, 다시 공유해도 같은 토큰이다")
  void sharesWithSameToken() throws Exception {
    Memory memory = memory(me, "내 추억");

    String token = shareToken(memory, me);
    assertThat(token).hasSize(32);
    share(memory, me)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.shareToken").value(token));

    Memory shared = memoryRepository.findById(memory.getId()).orElseThrow();
    assertThat(shared.getShareToken()).isEqualTo(token);
    assertThat(shared.getVisibilityType()).isEqualTo(MemoryVisibilityType.LINK);
  }

  @Test
  @DisplayName("남의 아카이브 · 삭제된 아카이브는 공유할 수 없다")
  void cannotShareOthersOrDeleted() throws Exception {
    Memory deleted = memory(me, "지운 추억");
    deleted.delete(LocalDateTime.of(2026, 10, 1, 0, 0));
    memoryRepository.save(deleted);

    share(memory(friend, "친구 추억"), me)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    share(deleted, me).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("링크로 받으면 받은 아카이브에 한 건 생기고, 여러 번 열어도 한 건이다")
  void receivesOnce() throws Exception {
    String token = shareToken(memory(friend, "친구 추억"), friend);

    receive(token, me).andExpect(status().isOk());
    receive(token, me).andExpect(status().isOk());

    assertThat(receivedMemoryRepository.findAll())
        .singleElement()
        .satisfies(
            received -> {
              assertThat(received.getReceiver().getId()).isEqualTo(me.getId());
              assertThat(received.getStatus()).isEqualTo(ReceivedMemoryStatus.PENDING);
            });
  }

  @Test
  @DisplayName("같은 링크를 여러 명이 받을 수 있다")
  void manyReceivers() throws Exception {
    String token = shareToken(memory(friend, "친구 추억"), friend);
    User third = saveUser("share-third", "셋째");

    receive(token, me).andExpect(status().isOk());
    receive(token, third).andExpect(status().isOk());

    assertThat(receivedMemoryRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("닫은 아카이브의 링크를 다시 열면 받은 아카이브로 돌아오고, 저장한 아카이브는 그대로다")
  void reopenAfterDismissKeepsSaved() throws Exception {
    Memory dismissedMemory = memory(friend, "닫을 추억");
    Memory savedMemory = memory(friend, "저장할 추억");
    String dismissedToken = shareToken(dismissedMemory, friend);
    String savedToken = shareToken(savedMemory, friend);
    receive(dismissedToken, me);
    receive(savedToken, me);
    update(dismissedMemory, ReceivedMemoryStatus.DISMISSED);
    update(savedMemory, ReceivedMemoryStatus.SAVED);

    receive(dismissedToken, me).andExpect(status().isOk());
    receive(savedToken, me).andExpect(status().isOk());

    assertThat(statusOf(dismissedMemory)).isEqualTo(ReceivedMemoryStatus.PENDING);
    assertThat(statusOf(savedMemory)).isEqualTo(ReceivedMemoryStatus.SAVED);
  }

  @Test
  @DisplayName("내 아카이브의 링크는 받을 수 없다")
  void cannotReceiveOwn() throws Exception {
    String token = shareToken(memory(me, "내 추억"), me);

    receive(token, me)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_RECEIVE_OWN_MEMORY"));
    assertThat(receivedMemoryRepository.count()).isZero();
  }

  @Test
  @DisplayName("없는 토큰 · 삭제된 아카이브의 토큰은 404 다")
  void unknownOrDeletedToken() throws Exception {
    Memory memory = memory(friend, "친구 추억");
    String token = shareToken(memory, friend);
    memory = memoryRepository.findById(memory.getId()).orElseThrow();
    memory.delete(LocalDateTime.of(2026, 10, 1, 0, 0));
    memoryRepository.save(memory);

    receive("unknown", me)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    receive(token, me).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    Memory memory = memory(me, "내 추억");

    mockMvc
        .perform(post("/v1/memories/{memoryId}/share", memory.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/v1/memories/shared/{token}", "any"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions share(Memory memory, User user) throws Exception {
    return mockMvc.perform(
        post("/v1/memories/{memoryId}/share", memory.getId()).header(AUTHORIZATION, bearer(user)));
  }

  private String shareToken(Memory memory, User owner) throws Exception {
    String body = share(memory, owner).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).path("data").path("shareToken").asText();
  }

  private ResultActions receive(String token, User user) throws Exception {
    return mockMvc.perform(
        post("/v1/memories/shared/{token}", token).header(AUTHORIZATION, bearer(user)));
  }

  private void update(Memory memory, ReceivedMemoryStatus status) {
    ReceivedMemory received =
        receivedMemoryRepository
            .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
            .orElseThrow();
    if (status == ReceivedMemoryStatus.SAVED) {
      received.save();
    } else {
      received.dismiss();
    }
    receivedMemoryRepository.save(received);
  }

  private ReceivedMemoryStatus statusOf(Memory memory) {
    return receivedMemoryRepository
        .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
        .orElseThrow()
        .getStatus();
  }

  private Memory memory(User owner, String title) {
    return memoryRepository.save(
        Memory.builder()
            .user(owner)
            .title(title)
            .content("내용")
            .memoryDate(LocalDate.of(2026, 8, 15))
            .build());
  }

  private String bearer(User user) {
    return "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    receivedMemoryRepository.deleteAllInBatch();
    memoryImageRepository.deleteAllInBatch();
    memoryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
