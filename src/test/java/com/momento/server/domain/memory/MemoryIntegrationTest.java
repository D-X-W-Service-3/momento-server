package com.momento.server.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.entity.MemoryVisibilityType;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
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

/** 추억 등록·목록 조회 API 를 실제 시큐리티 필터까지 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemoryIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private MemoryImageRepository memoryImageRepository;
  @Autowired private TokenProvider tokenProvider;

  private User me;
  private String token;

  @BeforeEach
  void setUp() {
    clearAll();
    me = userRepository.save(User.builder().kakaoId("kakao-me").nickname("서현").build());
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

  /** 이미지 업로드 URL 발급이 MEMORY 용도로 만드는 것과 같은 모양의 key. */
  private static String imageKey(User owner, String extension) {
    return "memories/" + owner.getId() + "/" + UUID.randomUUID() + "." + extension;
  }

  private ResultActions create(String body) throws Exception {
    return mockMvc.perform(
        post("/v1/memories")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Memory saveMemory(User owner, String title, LocalDate memoryDate) {
    return memoryRepository.save(
        Memory.builder().user(owner).title(title).memoryDate(memoryDate).build());
  }

  @Test
  @DisplayName("추억을 등록하면 data 없는 201 이고, PRIVATE 로 저장되며 이미지가 보낸 순서대로 붙는다")
  void createMemory() throws Exception {
    String first = imageKey(me, "jpg");
    String second = imageKey(me, "png");
    String body =
        """
        {
          "title": "제주도 여행",
          "content": "바다가 정말 예뻤다",
          "memoryDate": "2026-08-15",
          "imageKeys": ["%s", "%s"]
        }
        """
            .formatted(first, second);

    mockMvc
        .perform(
            post("/v1/memories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data").doesNotExist());

    Memory saved = memoryRepository.findAll().get(0);
    assertThat(saved.getTitle()).isEqualTo("제주도 여행");
    assertThat(saved.getContent()).isEqualTo("바다가 정말 예뻤다");
    assertThat(saved.getVisibilityType()).isEqualTo(MemoryVisibilityType.PRIVATE);
    assertThat(saved.getTimeCapsule()).isNull();

    List<MemoryImage> images =
        memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(saved.getId());
    assertThat(images).hasSize(2);
    assertThat(images.get(0).getDisplayOrder()).isZero();
    assertThat(images.get(0).getImageUrl()).isEqualTo(first);
    assertThat(images.get(1).getImageUrl()).isEqualTo(second);
  }

  @Test
  @DisplayName("이미지 없이도 등록에 성공한다")
  void createMemoryWithoutImages() throws Exception {
    String body =
        """
        {"title": "혼자 산책", "memoryDate": "2026-08-20"}
        """;

    mockMvc
        .perform(
            post("/v1/memories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    Memory saved = memoryRepository.findAll().get(0);
    assertThat(memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(saved.getId())).isEmpty();
  }

  @Test
  @DisplayName("토큰 없이 호출하면 401 을 반환한다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/memories"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("추억 날짜를 비우면 400 을 반환한다")
  void memoryDateIsRequired() throws Exception {
    String body =
        """
        {"title": "날짜 없음", "content": "내용"}
        """;

    mockMvc
        .perform(
            post("/v1/memories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

    assertThat(memoryRepository.count()).isZero();
  }

  @Test
  @DisplayName("이름이 15자 · 내용이 45자를 넘으면 400 이고, 딱 그 길이까지는 받는다")
  void titleAndContentLengthsAreLimited() throws Exception {
    String body = "{\"title\":\"%s\",\"content\":\"%s\",\"memoryDate\":\"2026-08-15\"}";

    for (String tooLong :
        List.of(body.formatted("가".repeat(16), "내용"), body.formatted("이름", "가".repeat(46)))) {
      mockMvc
          .perform(
              post("/v1/memories")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tooLong))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
    assertThat(memoryRepository.count()).isZero();

    mockMvc
        .perform(
            post("/v1/memories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted("가".repeat(15), "가".repeat(45))))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("사진을 9장 보내면 400 을 반환하고 아무것도 저장하지 않는다")
  void imageCountIsLimitedToEight() throws Exception {
    String keys =
        IntStream.rangeClosed(1, 9)
            .mapToObj(i -> "\"" + imageKey(me, "jpg") + "\"")
            .collect(Collectors.joining(","));
    String body =
        "{\"title\":\"사진 많음\",\"memoryDate\":\"2026-08-15\",\"imageKeys\":[" + keys + "]}";

    mockMvc
        .perform(
            post("/v1/memories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    assertThat(memoryRepository.count()).isZero();
    assertThat(memoryImageRepository.count()).isZero();
  }

  @Test
  @DisplayName("목록은 본인 추억만 memoryDate 내림차순으로, 대표 이미지 1장만 담아 반환한다")
  void findMyMemories() throws Exception {
    User other = userRepository.save(User.builder().kakaoId("kakao-other").nickname("남").build());
    saveMemory(other, "남의 추억", LocalDate.of(2026, 12, 31));

    saveMemory(me, "생일 파티", LocalDate.of(2026, 7, 2));
    Memory newer = saveMemory(me, "제주도 여행", LocalDate.of(2026, 8, 15));

    memoryImageRepository.saveAll(
        List.of(
            MemoryImage.builder()
                .memory(newer)
                .imageUrl("https://cdn.test/second.jpg")
                .displayOrder(1)
                .build(),
            MemoryImage.builder()
                .memory(newer)
                .imageUrl("https://cdn.test/first.jpg")
                .displayOrder(0)
                .build()));

    mockMvc
        .perform(get("/v1/memories").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.memories[0].title").value("제주도 여행"))
        .andExpect(jsonPath("$.data.memories[0].thumbnailUrl").value("https://cdn.test/first.jpg"))
        .andExpect(jsonPath("$.data.memories[1].title").value("생일 파티"))
        .andExpect(jsonPath("$.data.memories[1].thumbnailUrl").isEmpty())
        .andExpect(jsonPath("$.data.memories[0].imageCount").value(2))
        .andExpect(jsonPath("$.data.memories[1].imageCount").value(0));
  }

  @Test
  @DisplayName("제목 부분검색과 연도 필터로 목록을 좁힐 수 있다")
  void filterMemories() throws Exception {
    saveMemory(me, "제주도 여행", LocalDate.of(2026, 8, 15));
    saveMemory(me, "부산 여행", LocalDate.of(2025, 5, 1));
    saveMemory(me, "생일 파티", LocalDate.of(2026, 7, 2));

    mockMvc
        .perform(
            get("/v1/memories").param("title", "여행").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.totalElements").value(2));

    mockMvc
        .perform(
            get("/v1/memories").param("year", "2026").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.totalElements").value(2));

    mockMvc
        .perform(
            get("/v1/memories")
                .param("title", "여행")
                .param("year", "2026")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.memories[0].title").value("제주도 여행"));
  }

  @Test
  @DisplayName("이미지 key 가 비어 있거나 500자를 넘으면 400 을 반환한다")
  void imageKeyElementsAreValidated() throws Exception {
    String blank =
        """
        {"title": "빈 key", "memoryDate": "2026-08-15", "imageKeys": [""]}
        """;
    String nullKey =
        """
        {"title": "null key", "memoryDate": "2026-08-15", "imageKeys": [null]}
        """;
    String tooLong =
        "{\"title\":\"긴 key\",\"memoryDate\":\"2026-08-15\",\"imageKeys\":[\"memories/"
            + "a".repeat(600)
            + ".jpg\"]}";

    for (String body : List.of(blank, nullKey, tooLong)) {
      mockMvc
          .perform(
              post("/v1/memories")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }

    assertThat(memoryRepository.count()).isZero();
    assertThat(memoryImageRepository.count()).isZero();
  }

  @Test
  @DisplayName("허용 범위를 벗어난 연도가 오면 400 을 반환한다")
  void yearRangeIsValidated() throws Exception {
    for (String year : List.of("1000000000", "1899", "2101")) {
      mockMvc
          .perform(
              get("/v1/memories").param("year", year).header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
  }

  @Test
  @DisplayName("대표 사진 key 를 주면 그 사진이 첫 번째(display_order 0)가 되고 나머지는 보낸 순서를 지킨다")
  void representativeImageComesFirst() throws Exception {
    String a = imageKey(me, "jpg");
    String b = imageKey(me, "png");
    String c = imageKey(me, "webp");

    create(
            """
            {"title": "대표 사진", "memoryDate": "2026-08-15", "imageKeys": ["%s", "%s", "%s"],
             "representativeImageKey": "%s"}
            """
                .formatted(a, b, c, c))
        .andExpect(status().isCreated());

    Memory saved = memoryRepository.findAll().get(0);
    assertThat(memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(saved.getId()))
        .extracting(MemoryImage::getImageUrl)
        .containsExactly(c, a, b);
  }

  @Test
  @DisplayName("내 key 가 아니거나 중복이거나 대표 사진이 목록에 없으면 400 INVALID_IMAGE_KEY 이고 아무것도 저장하지 않는다")
  void rejectsInvalidImageKeys() throws Exception {
    User other = userRepository.save(User.builder().kakaoId("kakao-other").nickname("남").build());
    String mine = imageKey(me, "jpg");
    String profileKey = "profiles/" + me.getId() + "/" + UUID.randomUUID() + ".jpg";
    String body =
        """
        {"title": "잘못된 사진", "memoryDate": "2026-08-15", "imageKeys": [%s] %s}
        """;

    for (String invalid :
        List.of(
            body.formatted("\"" + imageKey(other, "jpg") + "\"", ""),
            body.formatted("\"https://cdn.test/a.jpg\"", ""),
            body.formatted("\"" + profileKey + "\"", ""),
            body.formatted("\"" + mine + "\", \"" + mine + "\"", ""),
            body.formatted(
                "\"" + mine + "\"",
                ", \"representativeImageKey\": \"" + imageKey(me, "png") + "\""))) {
      create(invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_IMAGE_KEY"));
    }

    assertThat(memoryRepository.count()).isZero();
    assertThat(memoryImageRepository.count()).isZero();
  }
}
