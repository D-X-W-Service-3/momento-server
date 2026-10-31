package com.momento.server.domain.calendar;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 통합 검색 API 를 검증한다. 오늘은 2026-10-29 로 고정한다.
 *
 * <p>캡슐은 개봉일이 미래면 WRITING, 지났으면 OPENED 로 둔다. 상태 전이 스케줄러가 돌아도 상태가 바뀌지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarSearchIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private AnniversaryRepository anniversaryRepository;
  @Autowired private MemoryRepository memoryRepository;

  @MockitoBean private Clock clock;

  private User me;
  private User other;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    me = saveUser("calendar-search-me", "나");
    other = saveUser("calendar-search-other", "남");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("검색어가 이름에 든 내 캡슐 · 기념일 · 추억을 종류별로 나눠 주고 totalCount 는 합이다")
  void splitsByTypeWithTotal() throws Exception {
    TimeCapsule capsule = capsule(me, "제주 캡슐", LocalDate.of(2027, 1, 1));
    capsule(me, "서울 캡슐", LocalDate.of(2027, 1, 1));
    capsule(other, "남의 제주", LocalDate.of(2027, 1, 1));
    Anniversary anniversary = anniversary(me, "제주 간 날", LocalDate.of(2026, 12, 1), RepeatType.NONE);
    anniversary(other, "남의 제주", LocalDate.of(2026, 12, 1), RepeatType.NONE);
    Memory memory = memory(me, "제주 바다", LocalDate.of(2026, 8, 15));
    memory(other, "남의 제주", LocalDate.of(2026, 8, 15));

    search("keyword", "제주")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(3))
        .andExpect(jsonPath("$.data.capsules.length()").value(1))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.capsules[0].memberCount").value(1))
        .andExpect(jsonPath("$.data.anniversaries.length()").value(1))
        .andExpect(jsonPath("$.data.anniversaries[0].anniversaryId").value(anniversary.getId()))
        .andExpect(jsonPath("$.data.anniversaries[0].date").value("2026-12-01"))
        .andExpect(jsonPath("$.data.anniversaries[0].dDay").value(33))
        .andExpect(jsonPath("$.data.memories.length()").value(1))
        .andExpect(jsonPath("$.data.memories[0].memoryId").value(memory.getId()));
  }

  @Test
  @DisplayName("기본은 최신순으로 날짜 내림차순이고, 같은 날짜면 ID 내림차순이다")
  void latestByDefault() throws Exception {
    TimeCapsule early = capsule(me, "제주 1", LocalDate.of(2027, 1, 1));
    TimeCapsule late = capsule(me, "제주 2", LocalDate.of(2027, 3, 1));
    Memory sameDayFirst = memory(me, "제주 a", LocalDate.of(2026, 8, 15));
    Memory sameDaySecond = memory(me, "제주 b", LocalDate.of(2026, 8, 15));
    Memory older = memory(me, "제주 c", LocalDate.of(2025, 1, 1));
    Anniversary soon = anniversary(me, "제주 기념", LocalDate.of(2026, 11, 1), RepeatType.NONE);
    Anniversary later = anniversary(me, "제주 기념 2", LocalDate.of(2027, 1, 1), RepeatType.NONE);

    search("keyword", "제주")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules[*].capsuleId", contains(id(late), id(early))))
        .andExpect(
            jsonPath(
                "$.data.memories[*].memoryId",
                contains(id(sameDaySecond), id(sameDayFirst), id(older))))
        .andExpect(
            jsonPath("$.data.anniversaries[*].anniversaryId", contains(id(later), id(soon))));
  }

  @Test
  @DisplayName("OLDEST 면 날짜 오름차순이고, 같은 날짜면 ID 오름차순이다")
  void oldest() throws Exception {
    TimeCapsule early = capsule(me, "제주 1", LocalDate.of(2027, 1, 1));
    TimeCapsule late = capsule(me, "제주 2", LocalDate.of(2027, 3, 1));
    Memory sameDayFirst = memory(me, "제주 a", LocalDate.of(2026, 8, 15));
    Memory sameDaySecond = memory(me, "제주 b", LocalDate.of(2026, 8, 15));
    Memory older = memory(me, "제주 c", LocalDate.of(2025, 1, 1));

    search("keyword", "제주", "sort", "OLDEST")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules[*].capsuleId", contains(id(early), id(late))))
        .andExpect(
            jsonPath(
                "$.data.memories[*].memoryId",
                contains(id(older), id(sameDayFirst), id(sameDaySecond))));
  }

  @Test
  @DisplayName("기간을 주면 카드 날짜가 그 해인 것만 주고, 매년 기념일은 그 해의 날짜로 한 건이다")
  void filtersByYear() throws Exception {
    TimeCapsule in2027 = capsule(me, "제주 2027", LocalDate.of(2027, 5, 1));
    capsule(me, "제주 2026", LocalDate.of(2026, 12, 1));
    capsule(me, "제주 2028", LocalDate.of(2028, 1, 1));
    Memory memoryIn2027 = memory(me, "제주 추억", LocalDate.of(2027, 3, 1));
    memory(me, "제주 지난 추억", LocalDate.of(2026, 3, 1));
    Anniversary yearly = anniversary(me, "제주 첫날", LocalDate.of(2024, 8, 12), RepeatType.YEARLY);
    anniversary(me, "제주 미래", LocalDate.of(2028, 1, 1), RepeatType.YEARLY);
    Anniversary once = anniversary(me, "제주 출발", LocalDate.of(2027, 2, 1), RepeatType.NONE);
    anniversary(me, "제주 작년", LocalDate.of(2026, 2, 1), RepeatType.NONE);

    search("keyword", "제주", "year", "2027")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(4))
        .andExpect(jsonPath("$.data.capsules[*].capsuleId", contains(id(in2027))))
        .andExpect(jsonPath("$.data.memories[*].memoryId", contains(id(memoryIn2027))))
        .andExpect(
            jsonPath("$.data.anniversaries[*].anniversaryId", contains(id(yearly), id(once))))
        .andExpect(jsonPath("$.data.anniversaries[0].date").value("2027-08-12"))
        .andExpect(jsonPath("$.data.anniversaries[1].date").value("2027-02-01"));
  }

  @Test
  @DisplayName("기간이 없으면 매년 기념일의 date 는 다가오는 날이다")
  void yearlyWithoutYearUsesNextDate() throws Exception {
    anniversary(me, "제주 첫날", LocalDate.of(2020, 8, 12), RepeatType.YEARLY);

    search("keyword", "제주")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.anniversaries.length()").value(1))
        .andExpect(jsonPath("$.data.anniversaries[0].date").value("2027-08-12"))
        .andExpect(jsonPath("$.data.anniversaries[0].dDay").value(287));
  }

  @Test
  @DisplayName("type 을 주면 다른 종류는 빈 목록이고 totalCount 도 그 종류만 센다")
  void filtersByType() throws Exception {
    capsule(me, "제주 캡슐", LocalDate.of(2027, 1, 1));
    anniversary(me, "제주 기념", LocalDate.of(2026, 12, 1), RepeatType.NONE);
    memory(me, "제주 1", LocalDate.of(2026, 8, 15));
    memory(me, "제주 2", LocalDate.of(2026, 8, 16));

    search("keyword", "제주", "type", "MEMORY")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(2))
        .andExpect(jsonPath("$.data.capsules", empty()))
        .andExpect(jsonPath("$.data.anniversaries", empty()))
        .andExpect(jsonPath("$.data.memories.length()").value(2));
  }

  @Test
  @DisplayName("검색어의 % 는 글자 그대로 찾는다")
  void percentIsLiteral() throws Exception {
    capsule(me, "100% 우정", LocalDate.of(2027, 1, 1));
    capsule(me, "우정", LocalDate.of(2027, 1, 1));
    anniversary(me, "100% 사랑", LocalDate.of(2026, 12, 1), RepeatType.NONE);
    anniversary(me, "사랑", LocalDate.of(2026, 12, 1), RepeatType.NONE);
    memory(me, "100% 행복", LocalDate.of(2026, 8, 15));
    memory(me, "행복", LocalDate.of(2026, 8, 15));

    search("keyword", "%")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(3))
        .andExpect(jsonPath("$.data.capsules[0].title").value("100% 우정"))
        .andExpect(jsonPath("$.data.anniversaries[0].title").value("100% 사랑"))
        .andExpect(jsonPath("$.data.memories[0].title").value("100% 행복"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "type=CAPSULE|검색어를 입력해 주세요.",
        "keyword=제주&year=1899|연도는 1900 이상 2100 이하여야 합니다.",
        "keyword=제주&type=ALL|요청 값이 올바르지 않습니다.",
        "keyword=제주&sort=NEW|요청 값이 올바르지 않습니다.",
        "keyword=제주&year=abc|요청 값이 올바르지 않습니다."
      })
  @DisplayName("요청 값이 올바르지 않으면 400 이다")
  void rejectsInvalidParams(String query, String message) throws Exception {
    mockMvc
        .perform(withToken(get("/v1/calendar/search?" + query)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value(message));
  }

  @Test
  @DisplayName("검색어가 공백뿐이면 400 이다")
  void rejectsBlankKeyword() throws Exception {
    search("keyword", "  ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("검색어를 입력해 주세요."));
  }

  @ParameterizedTest(name = "{0}자")
  @ValueSource(ints = {100, 101})
  @DisplayName("검색어는 100자까지 받는다")
  void keywordLength(int length) throws Exception {
    search("keyword", "가".repeat(length))
        .andExpect(length <= 100 ? status().isOk() : status().isBadRequest());
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/calendar/search").param("keyword", "제주"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions search(String... params) throws Exception {
    MockHttpServletRequestBuilder request = get("/v1/calendar/search");
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(withToken(request));
  }

  private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(
        AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1)));
  }

  private static int id(TimeCapsule capsule) {
    return capsule.getId().intValue();
  }

  private static int id(Memory memory) {
    return memory.getId().intValue();
  }

  private static int id(Anniversary anniversary) {
    return anniversary.getId().intValue();
  }

  private TimeCapsule capsule(User owner, String title, LocalDate openDate) {
    LocalDateTime openAt = openDate.atStartOfDay();
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title(title)
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(openDate.isAfter(TODAY) ? CapsuleStatus.WRITING : CapsuleStatus.OPENED)
                .letterDeadlineAt(openAt.minusDays(2))
                .openAt(openAt)
                .build());
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(owner)
            .role(MemberRole.OWNER)
            .status(MemberStatus.ACTIVE)
            .joinedAt(openAt.minusDays(30))
            .build());
    return capsule;
  }

  private Anniversary anniversary(User user, String title, LocalDate date, RepeatType repeatType) {
    return anniversaryRepository.save(
        Anniversary.create(user, title, date, repeatType, null, false, null, null));
  }

  private Memory memory(User user, String title, LocalDate date) {
    return memoryRepository.save(Memory.builder().user(user).title(title).memoryDate(date).build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    memoryRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    anniversaryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
