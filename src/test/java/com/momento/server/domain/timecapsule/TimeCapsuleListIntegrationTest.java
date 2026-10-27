package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
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
import java.util.ArrayList;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 목록 조회 API 를 시각을 고정해 검증한다.
 *
 * <p>고정 시각과 캡슐 일시를 모두 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 실제 오늘보다 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접
 * 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleListIntegrationTest {

  private static final Instant NOW = Instant.parse("2045-01-10T03:00:00Z");
  private static final LocalDateTime NOW_AT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final LocalDate TODAY = NOW_AT.toLocalDate();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private Clock clock;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    me = saveUser("list-me", "상래");
    friend = saveUser("list-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("카드에 명세의 필드가 모두 담기고, D-Day 는 고정한 오늘 기준이다")
  void returnsCards() throws Exception {
    TimeCapsule capsule =
        saveCapsule(
            "제주 여행",
            CapsuleStatus.WRITING,
            TODAY.plusDays(3).atTime(23, 59, 59),
            TODAY.plusDays(20));
    join(capsule, me, MemberRole.OWNER, MemberStatus.ACTIVE);
    join(capsule, friend, MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    saveLetter(capsule, friend, LetterStatus.SUBMITTED);
    saveLetter(capsule, me, LetterStatus.DRAFT);

    list(get("/v1/time-capsules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules", hasSize(1)))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.capsules[0].iconType").value("ICE_CREAM"))
        .andExpect(jsonPath("$.data.capsules[0].title").value("제주 여행"))
        .andExpect(jsonPath("$.data.capsules[0].description").value("설명"))
        .andExpect(jsonPath("$.data.capsules[0].status").value("WRITING"))
        .andExpect(jsonPath("$.data.capsules[0].openedByMe").value(false))
        .andExpect(jsonPath("$.data.capsules[0].openAt").value("2045-01-30T00:00:00"))
        .andExpect(jsonPath("$.data.capsules[0].dDay").value(20))
        .andExpect(jsonPath("$.data.capsules[0].deadlineDDay").value(3))
        .andExpect(jsonPath("$.data.capsules[0].memberCount").value(2))
        .andExpect(jsonPath("$.data.capsules[0].letterCount").value(1))
        .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  @DisplayName("확인한 열린 캡슐은 openedByMe 가 true 이고, 지난 개봉일은 음수 D-Day 다")
  void openedCapsuleCard() throws Exception {
    TimeCapsule capsule = mine("열림", CapsuleStatus.OPENED, TODAY.minusDays(30));
    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(capsule.getId(), me.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), NOW_AT));

    list(get("/v1/time-capsules"))
        .andExpect(jsonPath("$.data.capsules[0].openedByMe").value(true))
        .andExpect(jsonPath("$.data.capsules[0].dDay").value(-30))
        .andExpect(jsonPath("$.data.capsules[0].deadlineDDay").value(nullValue()));
  }

  @Test
  @DisplayName("남의 캡슐, 내보내진 캡슐, 삭제된 캡슐은 나오지 않고 개수에도 들어가지 않는다")
  void excludesOthersRemovedAndDeleted() throws Exception {
    TimeCapsule mine = mine("내 캡슐", CapsuleStatus.WRITING, TODAY.plusDays(20));
    join(
        saveCapsule("남의 캡슐", CapsuleStatus.WRITING, TODAY.plusDays(20)),
        friend,
        MemberRole.OWNER,
        MemberStatus.ACTIVE);
    join(
        saveCapsule("내보내진 캡슐", CapsuleStatus.WRITING, TODAY.plusDays(20)),
        me,
        MemberRole.PARTICIPANT,
        MemberStatus.REMOVED);
    TimeCapsule deleted =
        saveCapsule(
            "삭제된 캡슐",
            CapsuleStatus.WRITING,
            TODAY.plusDays(17).atTime(23, 59, 59),
            TODAY.plusDays(20),
            NOW_AT);
    join(deleted, me, MemberRole.OWNER, MemberStatus.ACTIVE);

    list(get("/v1/time-capsules"))
        .andExpect(jsonPath("$.data.capsules", hasSize(1)))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(mine.getId()))
        .andExpect(jsonPath("$.data.counts.all").value(1));
  }

  @Test
  @DisplayName("개봉일이 같은 캡슐을 페이지로 나눠 받아도 빠지거나 겹치지 않는다")
  void pagesWithoutGapsOrDuplicates() throws Exception {
    TimeCapsule a = mine("A", CapsuleStatus.WRITING, TODAY.plusDays(20));
    TimeCapsule b = mine("B", CapsuleStatus.WRITING, TODAY.plusDays(20));
    TimeCapsule c = mine("C", CapsuleStatus.WRITING, TODAY.plusDays(20));

    JsonNode first = data(list(get("/v1/time-capsules").param("size", "2")));
    JsonNode second =
        data(
            list(
                get("/v1/time-capsules")
                    .param("size", "2")
                    .param("cursor", first.get("nextCursor").asText())));

    assertThat(first.get("hasNext").asBoolean()).isTrue();
    assertThat(ids(first)).containsExactly(a.getId(), b.getId());
    assertThat(ids(second)).containsExactly(c.getId());
    assertThat(second.get("hasNext").asBoolean()).isFalse();
    assertThat(second.get("nextCursor").isNull()).isTrue();
  }

  @Test
  @DisplayName("size 를 보내지 않으면 20 개씩 준다")
  void defaultSizeIsTwenty() throws Exception {
    for (int i = 0; i < 21; i++) {
      mine("캡슐 " + i, CapsuleStatus.WRITING, TODAY.plusDays(20 + i));
    }

    list(get("/v1/time-capsules"))
        .andExpect(jsonPath("$.data.capsules", hasSize(20)))
        .andExpect(jsonPath("$.data.hasNext").value(true));
  }

  @Test
  @DisplayName("상태로 거르면 그 상태만 나오지만, 탭별 개수는 전체 기준이다")
  void statusFiltersCapsulesButNotCounts() throws Exception {
    TimeCapsule opened = mine("열림", CapsuleStatus.OPENED, TODAY.minusDays(1));
    mine("작성 중 1", CapsuleStatus.WRITING, TODAY.plusDays(20));
    mine("작성 중 2", CapsuleStatus.WRITING, TODAY.plusDays(21));
    mine("잠김", CapsuleStatus.LOCKED, TODAY.plusDays(5));

    list(get("/v1/time-capsules").param("status", "OPENED"))
        .andExpect(jsonPath("$.data.capsules", hasSize(1)))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(opened.getId()))
        .andExpect(jsonPath("$.data.counts.all").value(4))
        .andExpect(jsonPath("$.data.counts.opened").value(1))
        .andExpect(jsonPath("$.data.counts.writing").value(2))
        .andExpect(jsonPath("$.data.counts.locked").value(1));
  }

  @Test
  @DisplayName("검색어는 목록과 탭별 개수 모두에 적용되고, 멤버 닉네임으로도 찾는다")
  void keywordAppliesToCapsulesAndCounts() throws Exception {
    TimeCapsule byNickname = mine("캡슐", CapsuleStatus.LOCKED, TODAY.plusDays(5));
    join(byNickname, saveUser("list-jeju", "제주사람"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    TimeCapsule byTitle = mine("제주 여행", CapsuleStatus.WRITING, TODAY.plusDays(20));
    mine("서울 여행", CapsuleStatus.WRITING, TODAY.plusDays(21));

    list(get("/v1/time-capsules").param("keyword", " 제주 "))
        .andExpect(jsonPath("$.data.capsules", hasSize(2)))
        .andExpect(jsonPath("$.data.capsules[0].capsuleId").value(byNickname.getId()))
        .andExpect(jsonPath("$.data.capsules[1].capsuleId").value(byTitle.getId()))
        .andExpect(jsonPath("$.data.counts.all").value(2))
        .andExpect(jsonPath("$.data.counts.writing").value(1))
        .andExpect(jsonPath("$.data.counts.locked").value(1));
  }

  @Test
  @DisplayName("캡슐이 없으면 빈 목록과 0 개수를 준다")
  void emptyList() throws Exception {
    list(get("/v1/time-capsules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsules", hasSize(0)))
        .andExpect(jsonPath("$.data.counts.all").value(0))
        .andExpect(jsonPath("$.data.counts.opened").value(0))
        .andExpect(jsonPath("$.data.counts.writing").value(0))
        .andExpect(jsonPath("$.data.counts.locked").value(0))
        .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @ParameterizedTest(name = "size={0}")
  @ValueSource(strings = {"0", "101", "abc"})
  @DisplayName("size 가 1~100 밖이거나 숫자가 아니면 400 이다")
  void rejectsInvalidSize(String size) throws Exception {
    list(get("/v1/time-capsules").param("size", size))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("없는 상태 값은 400 이다")
  void rejectsUnknownStatus() throws Exception {
    list(get("/v1/time-capsules").param("status", "DELETED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("깨진 커서는 400 이다")
  void rejectsBrokenCursor() throws Exception {
    list(get("/v1/time-capsules").param("cursor", "broken!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
  }

  @Test
  @DisplayName("인증 없이 조회할 수 없다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/time-capsules"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions list(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.header(
            AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1))));
  }

  private JsonNode data(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
  }

  private List<Long> ids(JsonNode data) {
    List<Long> ids = new ArrayList<>();
    data.get("capsules").forEach(card -> ids.add(card.get("capsuleId").asLong()));
    return ids;
  }

  private TimeCapsule mine(String title, CapsuleStatus status, LocalDate openDate) {
    TimeCapsule capsule = saveCapsule(title, status, openDate);
    join(capsule, me, MemberRole.OWNER, MemberStatus.ACTIVE);
    return capsule;
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(String title, CapsuleStatus status, LocalDate openDate) {
    return saveCapsule(title, status, openDate.minusDays(3).atTime(23, 59, 59), openDate);
  }

  private TimeCapsule saveCapsule(
      String title, CapsuleStatus status, LocalDateTime letterDeadlineAt, LocalDate openDate) {
    return saveCapsule(title, status, letterDeadlineAt, openDate, null);
  }

  private TimeCapsule saveCapsule(
      String title,
      CapsuleStatus status,
      LocalDateTime letterDeadlineAt,
      LocalDate openDate,
      LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title(title)
            .description("설명")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(letterDeadlineAt)
            .openAt(openDate.atStartOfDay())
            .deletedAt(deletedAt)
            .build());
  }

  private void join(TimeCapsule capsule, User user, MemberRole role, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(NOW_AT)
            .build());
  }

  private void saveLetter(TimeCapsule capsule, User author, LetterStatus status) {
    letterRepository.save(
        Letter.builder().timeCapsule(capsule).author(author).content("편지").status(status).build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
