package com.momento.server.domain.memory;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.ReceivedMemory;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/** 공유받아 저장한 아카이브가 내 아카이브처럼 보이고, 수정 · 삭제는 원본 주인만 되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class SavedMemoryIntegrationTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private MemoryImageRepository memoryImageRepository;
  @Autowired private ReceivedMemoryRepository receivedMemoryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("saved-api-me", "나");
    friend = saveUser("saved-api-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("저장한 아카이브 상세는 작성자가 공유한 사람이고 isMine 은 false 다")
  void savedDetail() throws Exception {
    Memory memory = memory(friend, "친구 추억");
    receive(memory, true);

    mockMvc
        .perform(withToken(get("/v1/memories/{id}", memory.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("친구 추억"))
        .andExpect(jsonPath("$.data.authorNickname").value("유빈"))
        .andExpect(jsonPath("$.data.isMine").value(false));
  }

  @Test
  @DisplayName("내 아카이브 상세는 그대로 isMine 이 true 다")
  void myDetail() throws Exception {
    Memory memory = memory(me, "내 추억");

    mockMvc
        .perform(withToken(get("/v1/memories/{id}", memory.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authorNickname").value("나"))
        .andExpect(jsonPath("$.data.isMine").value(true));
  }

  @Test
  @DisplayName("받기만 한 아카이브와 원본이 삭제된 아카이브의 상세는 404 다")
  void notViewable() throws Exception {
    Memory pending = memory(friend, "받기만 함");
    receive(pending, false);
    Memory deleted = memory(friend, "지워짐");
    receive(deleted, true);
    deleted.delete(NOW);
    memoryRepository.save(deleted);

    mockMvc
        .perform(withToken(get("/v1/memories/{id}", pending.getId())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(withToken(get("/v1/memories/{id}", deleted.getId())))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("저장한 아카이브는 수정 · 삭제할 수 없다")
  void cannotEditSaved() throws Exception {
    Memory memory = memory(friend, "친구 추억");
    receive(memory, true);

    mockMvc
        .perform(
            withToken(patch("/v1/memories/{id}", memory.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"바꿈\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(withToken(delete("/v1/memories/{id}", memory.getId())))
        .andExpect(status().isNotFound());
  }

  private void receive(Memory memory, boolean save) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          receivedMemoryRepository.insertIfAbsent(memory.getId(), me.getId(), NOW);
          if (save) {
            receivedMemoryRepository
                .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
                .ifPresent(ReceivedMemory::save);
          }
        });
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

  private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(
        AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1)));
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
