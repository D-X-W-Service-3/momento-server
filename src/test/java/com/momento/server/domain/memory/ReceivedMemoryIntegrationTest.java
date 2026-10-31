package com.momento.server.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** 받은 아카이브 조회 · 저장 · 닫기 API 를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ReceivedMemoryIntegrationTest {

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
    me = saveUser("received-api-me", "나");
    friend = saveUser("received-api-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("받은 아카이브를 받은 순으로, 보낸 사람 닉네임과 사진까지 준다")
  void listsReceived() throws Exception {
    Memory first = memory(friend, "2024 우리, 다시 만나는 날");
    image(first, "memories/" + friend.getId() + "/b.jpg", 1);
    image(first, "memories/" + friend.getId() + "/a.jpg", 0);
    Memory second = memory(friend, "둘째");
    ReceivedMemory firstReceived = receive(first);
    ReceivedMemory secondReceived = receive(second);

    received()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memories.length()").value(2))
        .andExpect(jsonPath("$.data.memories[0].receivedMemoryId").value(firstReceived.getId()))
        .andExpect(jsonPath("$.data.memories[0].senderNickname").value("유빈"))
        .andExpect(jsonPath("$.data.memories[0].title").value("2024 우리, 다시 만나는 날"))
        .andExpect(jsonPath("$.data.memories[0].content").value("내용"))
        .andExpect(jsonPath("$.data.memories[0].memoryDate").value("2026-08-15"))
        .andExpect(jsonPath("$.data.memories[0].images.length()").value(2))
        .andExpect(jsonPath("$.data.memories[0].images[0].displayOrder").value(0))
        .andExpect(jsonPath("$.data.memories[0].images[0].isRepresentative").value(true))
        .andExpect(jsonPath("$.data.memories[0].images[0].imageUrl").value(containsString("a.jpg")))
        .andExpect(jsonPath("$.data.memories[0].images[0].imageKey").doesNotExist())
        .andExpect(jsonPath("$.data.memories[0].images[1].isRepresentative").value(false))
        .andExpect(jsonPath("$.data.memories[1].receivedMemoryId").value(secondReceived.getId()))
        .andExpect(jsonPath("$.data.memories[1].images").value(empty()));
  }

  @Test
  @DisplayName("원본이 삭제된 아카이브와 남이 받은 아카이브는 나오지 않는다")
  void hidesDeletedAndOthers() throws Exception {
    Memory deleted = memory(friend, "지워짐");
    receive(deleted);
    deleted.delete(NOW);
    memoryRepository.save(deleted);
    Memory othersReceived = memory(friend, "남이 받음");
    transactionTemplate.executeWithoutResult(
        tx ->
            receivedMemoryRepository.insertIfAbsent(
                othersReceived.getId(), saveUser("received-api-third", "셋째").getId(), NOW));

    received().andExpect(status().isOk()).andExpect(jsonPath("$.data.memories").value(empty()));
  }

  @Test
  @DisplayName("저장하면 201 이고 받은 아카이브 조회에서 빠진다")
  void saves() throws Exception {
    ReceivedMemory received = receive(memory(friend, "친구 추억"));

    save(received.getId()).andExpect(status().isCreated());

    assertThat(statusOf(received)).isEqualTo(ReceivedMemoryStatus.SAVED);
    received().andExpect(jsonPath("$.data.memories").value(empty()));
  }

  @Test
  @DisplayName("닫으면 받은 아카이브 조회에서 빠진다")
  void dismisses() throws Exception {
    ReceivedMemory received = receive(memory(friend, "친구 추억"));

    dismiss(received.getId()).andExpect(status().isOk());

    assertThat(statusOf(received)).isEqualTo(ReceivedMemoryStatus.DISMISSED);
    received().andExpect(jsonPath("$.data.memories").value(empty()));
  }

  @Test
  @DisplayName("이미 처리했거나 남의 것이거나 원본이 삭제됐으면 404 다")
  void notFound() throws Exception {
    ReceivedMemory saved = receive(memory(friend, "저장함"));
    save(saved.getId()).andExpect(status().isCreated());
    Memory deletedMemory = memory(friend, "지워짐");
    ReceivedMemory deleted = receive(deletedMemory);
    deletedMemory.delete(NOW);
    memoryRepository.save(deletedMemory);
    Memory othersMemory = memory(me, "내 추억");
    ReceivedMemory others =
        transactionTemplate.execute(
            tx -> {
              receivedMemoryRepository.insertIfAbsent(othersMemory.getId(), friend.getId(), NOW);
              return receivedMemoryRepository
                  .findByMemoryIdAndReceiverId(othersMemory.getId(), friend.getId())
                  .orElseThrow();
            });

    for (Long id : List.of(saved.getId(), deleted.getId(), others.getId(), -1L)) {
      save(id)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("RECEIVED_MEMORY_NOT_FOUND"));
      dismiss(id).andExpect(status().isNotFound());
    }
  }

  @Test
  @DisplayName("아카이브 목록의 배너는 받은 아카이브가 있을 때만 true 다")
  void bannerOnArchiveList() throws Exception {
    archiveList().andExpect(jsonPath("$.data.hasReceivedMemory").value(false));

    ReceivedMemory received = receive(memory(friend, "친구 추억"));
    archiveList().andExpect(jsonPath("$.data.hasReceivedMemory").value(true));

    dismiss(received.getId()).andExpect(status().isOk());
    archiveList().andExpect(jsonPath("$.data.hasReceivedMemory").value(false));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/v1/memories/received")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/v1/memories/received/1/save")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/v1/memories/received/1/dismiss")).andExpect(status().isUnauthorized());
  }

  private ResultActions received() throws Exception {
    return mockMvc.perform(get("/v1/memories/received").header(AUTHORIZATION, bearer()));
  }

  private ResultActions archiveList() throws Exception {
    return mockMvc.perform(get("/v1/memories").header(AUTHORIZATION, bearer()));
  }

  private ResultActions save(Long id) throws Exception {
    return mockMvc.perform(
        post("/v1/memories/received/{id}/save", id).header(AUTHORIZATION, bearer()));
  }

  private ResultActions dismiss(Long id) throws Exception {
    return mockMvc.perform(
        post("/v1/memories/received/{id}/dismiss", id).header(AUTHORIZATION, bearer()));
  }

  private ReceivedMemory receive(Memory memory) {
    return transactionTemplate.execute(
        tx -> {
          receivedMemoryRepository.insertIfAbsent(memory.getId(), me.getId(), NOW);
          return receivedMemoryRepository
              .findByMemoryIdAndReceiverId(memory.getId(), me.getId())
              .orElseThrow();
        });
  }

  private ReceivedMemoryStatus statusOf(ReceivedMemory received) {
    return receivedMemoryRepository.findById(received.getId()).orElseThrow().getStatus();
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

  private void image(Memory memory, String key, int order) {
    memoryImageRepository.save(
        MemoryImage.builder().memory(memory).imageUrl(key).displayOrder(order).build());
  }

  private String bearer() {
    return "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1));
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
