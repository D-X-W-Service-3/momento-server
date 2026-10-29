package com.momento.server.domain.calendar;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 캘린더 날짜별 조회 API 를 검증한다. 오늘은 2026-10-29 로 고정한다.
 *
 * <p>조회하는 날은 2026-11-05 다. 그 날 열리는 캡슐은 편지 마감이 지나 상태 전이 스케줄러가 돌아도 LOCKED 그대로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarDailyIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
  private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private AnniversaryRepository anniversaryRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private MemoryImageRepository memoryImageRepository;

  @MockitoBean private Clock clock;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    me = saveUser("calendar-daily-me", "나");
    friend = saveUser("calendar-daily-friend", "친구");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("그 날 열리는 캡슐을 목록 카드 형태로, 개봉일 순으로 준다")
  void capsulesOpeningThatDay() throws Exception {
    TimeCapsule midnight = capsule(me, DAY.atStartOfDay(), "자정");
    join(midnight, friend);
    letter(midnight, me, LetterStatus.SUBMITTED);
    letter(midnight, friend, LetterStatus.DRAFT);
    TimeCapsule lastMinute = capsule(me, DAY.atTime(23, 59), "마지막 1분");
    capsule(me, DAY.minusDays(1).atTime(23, 59), "전날");
    capsule(me, DAY.plusDays(1).atStartOfDay(), "다음 날");
    capsule(friend, DAY.atStartOfDay(), "남의 캡슐");

    daily(DAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-11-05"))
        .andExpect(jsonPath("$.data.capsules.length()").value(2))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(midnight.getId()))
        .andExpect(jsonPath("$.data.capsules[0].title").value("자정"))
        .andExpect(jsonPath("$.data.capsules[0].status").value("LOCKED"))
        .andExpect(jsonPath("$.data.capsules[0].openedByMe").value(false))
        .andExpect(jsonPath("$.data.capsules[0].openAt").value("2026-11-05T00:00:00"))
        .andExpect(jsonPath("$.data.capsules[0].dDay").value(7))
        .andExpect(jsonPath("$.data.capsules[0].deadlineDDay").value(nullValue()))
        .andExpect(jsonPath("$.data.capsules[0].memberCount").value(2))
        .andExpect(jsonPath("$.data.capsules[0].letterCount").value(1))
        .andExpect(jsonPath("$.data.capsules[1].capsuleId").value(lastMinute.getId()));
  }

  @Test
  @DisplayName("그 날이 기념일인 기념일을 준다. 매년 반복은 그 해 날짜로, 등록한 해보다 앞선 해는 빠진다")
  void anniversariesThatDay() throws Exception {
    Anniversary yearly = anniversary(me, "만난 날", LocalDate.of(2020, 11, 5), RepeatType.YEARLY);
    Anniversary once = anniversary(me, "출발", DAY, RepeatType.NONE);
    anniversary(me, "미래", LocalDate.of(2030, 11, 5), RepeatType.YEARLY);
    anniversary(me, "다른 날", DAY.plusDays(1), RepeatType.NONE);
    anniversary(me, "지난 해", LocalDate.of(2025, 11, 5), RepeatType.NONE);
    anniversary(friend, "남의 기념일", DAY, RepeatType.NONE);

    daily(DAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.anniversaries.length()").value(2))
        .andExpect(jsonPath("$.data.anniversaries[0].anniversaryId").value(yearly.getId()))
        .andExpect(jsonPath("$.data.anniversaries[0].title").value("만난 날"))
        .andExpect(jsonPath("$.data.anniversaries[0].date").value("2026-11-05"))
        .andExpect(jsonPath("$.data.anniversaries[0].dDay").value(7))
        .andExpect(jsonPath("$.data.anniversaries[1].anniversaryId").value(once.getId()));
  }

  @Test
  @DisplayName("지난 날짜의 기념일은 dDay 가 음수다")
  void pastAnniversary() throws Exception {
    anniversary(me, "만난 날", LocalDate.of(2020, 8, 12), RepeatType.YEARLY);

    daily(LocalDate.of(2026, 8, 12))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.anniversaries[0].date").value("2026-08-12"))
        .andExpect(jsonPath("$.data.anniversaries[0].dDay").value(-78));
  }

  @Test
  @DisplayName("그 날 추억을 아카이브 목록 항목 형태로 준다")
  void memoriesThatDay() throws Exception {
    Memory memory = memory(me, DAY);
    memoryImageRepository.saveAll(
        List.of(image(memory, "memories/1/b.jpg", 1), image(memory, "memories/1/a.jpg", 0)));
    memory(me, DAY.plusDays(1));
    memory(friend, DAY);

    daily(DAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memories.length()").value(1))
        .andExpect(jsonPath("$.data.memories[0].memoryId").value(memory.getId()))
        .andExpect(jsonPath("$.data.memories[0].title").value("추억"))
        .andExpect(jsonPath("$.data.memories[0].memoryDate").value("2026-11-05"))
        .andExpect(jsonPath("$.data.memories[0].thumbnailUrl").value("memories/1/a.jpg"))
        .andExpect(jsonPath("$.data.memories[0].imageCount").value(2));
  }

  @Test
  @DisplayName("type 을 주면 다른 종류는 빈 목록이다")
  void filtersByType() throws Exception {
    capsule(me, DAY.atStartOfDay(), "캡슐");
    anniversary(me, "기념일", DAY, RepeatType.NONE);
    memory(me, DAY);

    mockMvc
        .perform(
            withToken(
                get("/v1/calendar/daily").param("date", "2026-11-05").param("type", "ANNIVERSARY")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules", empty()))
        .andExpect(jsonPath("$.data.anniversaries.length()").value(1))
        .andExpect(jsonPath("$.data.memories", empty()));
  }

  @Test
  @DisplayName("일정이 없는 날은 세 목록이 모두 비어 있다")
  void emptyDay() throws Exception {
    daily(DAY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules", empty()))
        .andExpect(jsonPath("$.data.anniversaries", empty()))
        .andExpect(jsonPath("$.data.memories", empty()));
  }

  @Test
  @DisplayName("date 가 없으면 400 이다")
  void rejectsMissingDate() throws Exception {
    mockMvc
        .perform(withToken(get("/v1/calendar/daily")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("날짜를 입력해 주세요."));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"date=2026-13-01", "date=20261105", "date=2026-11-05&type=ALL"})
  @DisplayName("date 형식이 틀리거나 type 이 없는 값이면 400 과 기본 메시지다")
  void rejectsUnconvertibleParams(String query) throws Exception {
    mockMvc
        .perform(withToken(get("/v1/calendar/daily?" + query)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/calendar/daily").param("date", "2026-11-05"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions daily(LocalDate date) throws Exception {
    return mockMvc.perform(withToken(get("/v1/calendar/daily").param("date", date.toString())));
  }

  private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(
        AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1)));
  }

  /** 편지 마감은 개봉 10일 전이라 오늘 기준으로 이미 지났다. 상태는 LOCKED 로 둔다. */
  private TimeCapsule capsule(User owner, LocalDateTime openAt, String title) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title(title)
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(CapsuleStatus.LOCKED)
                .letterDeadlineAt(openAt.minusDays(10))
                .openAt(openAt)
                .build());
    capsuleMemberRepository.save(member(capsule, owner, MemberRole.OWNER));
    return capsule;
  }

  private void join(TimeCapsule capsule, User user) {
    capsuleMemberRepository.save(member(capsule, user, MemberRole.PARTICIPANT));
  }

  private CapsuleMember member(TimeCapsule capsule, User user, MemberRole role) {
    return CapsuleMember.builder()
        .timeCapsule(capsule)
        .user(user)
        .role(role)
        .status(MemberStatus.ACTIVE)
        .joinedAt(capsule.getOpenAt().minusDays(30))
        .build();
  }

  private void letter(TimeCapsule capsule, User author, LetterStatus status) {
    letterRepository.save(
        Letter.builder().timeCapsule(capsule).author(author).content("편지").status(status).build());
  }

  private Anniversary anniversary(User user, String title, LocalDate date, RepeatType repeatType) {
    return anniversaryRepository.save(
        Anniversary.create(user, title, date, repeatType, null, false, null, null));
  }

  private Memory memory(User user, LocalDate date) {
    return memoryRepository.save(Memory.builder().user(user).title("추억").memoryDate(date).build());
  }

  private static MemoryImage image(Memory memory, String url, int order) {
    return MemoryImage.builder().memory(memory).imageUrl(url).displayOrder(order).build();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    memoryImageRepository.deleteAllInBatch();
    memoryRepository.deleteAllInBatch();
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    anniversaryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
