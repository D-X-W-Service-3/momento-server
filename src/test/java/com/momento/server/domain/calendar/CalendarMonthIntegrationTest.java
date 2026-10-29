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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 캘린더 월별 조회 API 를 검증한다. 오늘은 2026-10-29 로 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarMonthIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
  private static final LocalDateTime NOW_DATE_TIME = LocalDateTime.of(2026, 10, 29, 3, 0);

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
    me = saveUser("calendar-me", "나");
    other = saveUser("calendar-other", "남");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("일정이 있는 날짜만 오름차순으로, 날짜마다 CAPSULE · ANNIVERSARY · MEMORY 순으로 준다")
  void mergesDaysInOrder() throws Exception {
    memory(me, LocalDate.of(2026, 8, 12));
    anniversary(me, LocalDate.of(2026, 8, 12), RepeatType.NONE);
    capsule(me, LocalDateTime.of(2026, 8, 12, 0, 0));
    capsule(me, LocalDateTime.of(2026, 8, 12, 0, 0));
    capsule(me, LocalDateTime.of(2026, 8, 1, 0, 0));
    memory(me, LocalDate.of(2026, 8, 31));
    capsule(me, LocalDateTime.of(2026, 9, 1, 0, 0));
    memory(me, LocalDate.of(2026, 7, 31));

    month(2026, 8)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.year").value(2026))
        .andExpect(jsonPath("$.data.month").value(8))
        .andExpect(jsonPath("$.data.days.length()").value(3))
        .andExpect(jsonPath("$.data.days[0].date").value("2026-08-01"))
        .andExpect(jsonPath("$.data.days[0].types", contains("CAPSULE")))
        .andExpect(jsonPath("$.data.days[1].date").value("2026-08-12"))
        .andExpect(jsonPath("$.data.days[1].types", contains("CAPSULE", "ANNIVERSARY", "MEMORY")))
        .andExpect(jsonPath("$.data.days[2].date").value("2026-08-31"))
        .andExpect(jsonPath("$.data.days[2].types", contains("MEMORY")));
  }

  @Test
  @DisplayName("남의 것 · 내보내진 캡슐 · 삭제된 캡슐 · 삭제된 추억 · 캡슐에서 옮긴 추억은 찍지 않는다")
  void marksOnlyMine() throws Exception {
    capsule(other, LocalDateTime.of(2026, 8, 3, 0, 0));
    TimeCapsule removed = saveCapsule(LocalDateTime.of(2026, 8, 4, 0, 0), null);
    join(removed, me, MemberStatus.REMOVED);
    TimeCapsule deleted = saveCapsule(LocalDateTime.of(2026, 8, 5, 0, 0), NOW_DATE_TIME);
    join(deleted, me, MemberStatus.ACTIVE);
    anniversary(other, LocalDate.of(2026, 8, 6), RepeatType.NONE);
    memory(other, LocalDate.of(2026, 8, 7));
    memoryRepository.save(
        Memory.builder()
            .user(me)
            .title("삭제")
            .memoryDate(LocalDate.of(2026, 8, 8))
            .deletedAt(NOW_DATE_TIME)
            .build());
    memoryRepository.save(
        Memory.builder()
            .user(me)
            .timeCapsule(deleted)
            .title("옮김")
            .memoryDate(LocalDate.of(2026, 8, 9))
            .build());

    month(2026, 8)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days", empty()))
        .andExpect(jsonPath("$.data.counts.all").value(0));
  }

  @Test
  @DisplayName("매년 기념일은 이후 해의 같은 달에도 찍히고, 등록한 해보다 앞선 해에는 찍히지 않는다")
  void yearlyAnniversary() throws Exception {
    anniversary(me, LocalDate.of(2024, 8, 5), RepeatType.YEARLY);
    anniversary(me, LocalDate.of(2030, 8, 7), RepeatType.YEARLY);
    anniversary(me, LocalDate.of(2024, 8, 9), RepeatType.NONE);

    month(2026, 8)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(1))
        .andExpect(jsonPath("$.data.days[0].date").value("2026-08-05"))
        .andExpect(jsonPath("$.data.days[0].types", contains("ANNIVERSARY")));
  }

  @Test
  @DisplayName("2월 29일 매년 기념일은 평년에 2월 28일에 찍힌다")
  void leapDayAnniversary() throws Exception {
    anniversary(me, LocalDate.of(2024, 2, 29), RepeatType.YEARLY);

    month(2027, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].date").value("2027-02-28"));
    month(2028, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].date").value("2028-02-29"));
  }

  @Test
  @DisplayName("type 을 주면 그 종류만 찍고, counts 는 그대로 전체다")
  void filtersByType() throws Exception {
    capsule(me, LocalDateTime.of(2026, 8, 1, 0, 0));
    anniversary(me, LocalDate.of(2026, 8, 12), RepeatType.NONE);
    memory(me, LocalDate.of(2026, 8, 12));

    mockMvc
        .perform(authorized(2026, 8).param("type", "CAPSULE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days.length()").value(1))
        .andExpect(jsonPath("$.data.days[0].date").value("2026-08-01"))
        .andExpect(jsonPath("$.data.days[0].types", contains("CAPSULE")))
        .andExpect(jsonPath("$.data.counts.all").value(3))
        .andExpect(jsonPath("$.data.counts.capsule").value(1))
        .andExpect(jsonPath("$.data.counts.anniversary").value(1))
        .andExpect(jsonPath("$.data.counts.memory").value(1));
  }

  @Test
  @DisplayName("counts 는 다른 달 일정까지 전체 기간으로 세고, 매년 기념일은 한 개로 센다")
  void countsAllPeriods() throws Exception {
    capsule(me, LocalDateTime.of(2026, 8, 1, 0, 0));
    capsule(me, LocalDateTime.of(2027, 1, 1, 0, 0));
    capsule(me, LocalDateTime.of(2025, 3, 1, 0, 0));
    anniversary(me, LocalDate.of(2020, 8, 12), RepeatType.YEARLY);
    memory(me, LocalDate.of(2026, 8, 12));
    memory(me, LocalDate.of(2019, 1, 1));

    month(2026, 8)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.counts.all").value(6))
        .andExpect(jsonPath("$.data.counts.capsule").value(3))
        .andExpect(jsonPath("$.data.counts.memory").value(2))
        .andExpect(jsonPath("$.data.counts.anniversary").value(1));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"month=8", "year=1899&month=8", "year=2101&month=8", "year=2026"})
  @DisplayName("year · month 가 없거나 year 가 범위 밖이면 400 이다")
  void rejectsInvalidParams(String query) throws Exception {
    mockMvc
        .perform(withToken(get("/v1/calendar?" + query)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @ParameterizedTest(name = "month={0}")
  @ValueSource(ints = {0, 13})
  @DisplayName("month 가 1~12 밖이면 400 과 범위 메시지다")
  void rejectsMonthOutOfRange(int month) throws Exception {
    month(2026, month)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("월은 1 이상 12 이하여야 합니다."));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"year=2026&month=8&type=ALL", "year=abc&month=8"})
  @DisplayName("값을 변환할 수 없으면 400 과 기본 메시지다. 내부 클래스명을 싣지 않는다")
  void rejectsUnconvertibleParams(String query) throws Exception {
    mockMvc
        .perform(withToken(get("/v1/calendar?" + query)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/calendar").param("year", "2026").param("month", "8"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions month(int year, int month) throws Exception {
    return mockMvc.perform(authorized(year, month));
  }

  private MockHttpServletRequestBuilder authorized(int year, int month) {
    return withToken(
        get("/v1/calendar")
            .param("year", String.valueOf(year))
            .param("month", String.valueOf(month)));
  }

  private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
    return request.header(
        AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1)));
  }

  private void capsule(User user, LocalDateTime openAt) {
    join(saveCapsule(openAt, null), user, MemberStatus.ACTIVE);
  }

  private TimeCapsule saveCapsule(LocalDateTime openAt, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(CapsuleStatus.OPENED)
            .letterDeadlineAt(openAt.minusDays(10))
            .openAt(openAt)
            .deletedAt(deletedAt)
            .build());
  }

  private void join(TimeCapsule capsule, User user, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(MemberRole.OWNER)
            .status(status)
            .joinedAt(capsule.getOpenAt().minusDays(30))
            .build());
  }

  private void anniversary(User user, LocalDate date, RepeatType repeatType) {
    anniversaryRepository.save(
        Anniversary.create(user, "기념일", date, repeatType, null, false, null, null));
  }

  private void memory(User user, LocalDate date) {
    memoryRepository.save(Memory.builder().user(user).title("추억").memoryDate(date).build());
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
