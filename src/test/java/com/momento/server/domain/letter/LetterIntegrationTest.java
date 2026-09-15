package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 인증 필터와 트랜잭션 커밋을 포함한다. 테스트 전체를 트랜잭션으로 감싸지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
class LetterIntegrationTest {
  private static final String BODY = "{\"content\":\"내 편지\",\"themeType\":\"WATERCOLOR\"}";
  @Autowired private MockMvc mvc;
  @Autowired private TokenProvider tokens;
  @Autowired private UserRepository users;
  @Autowired private TimeCapsuleRepository capsules;
  @Autowired private CapsuleMemberRepository members;
  @Autowired private LetterRepository letters;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private Clock clock;
  private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
  private User owner;
  private TimeCapsule capsule;
  private String token;

  @BeforeEach
  void setUp() {
    clean();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    owner = users.save(User.builder().kakaoId("letter-owner").nickname("작성자").build());
    capsule =
        capsules.save(
            TimeCapsule.builder()
                .creator(owner)
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .openAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1))
                .build());
    join(owner);
    token = tokens.generateToken(owner, Duration.ofHours(1));
  }

  @AfterEach
  void clean() {
    letters.deleteAll();
    members.deleteAll();
    capsules.deleteAll();
    users.deleteAll();
  }

  @Test
  void createsDraftAndReadsOwnLetter() throws Exception {
    create(token, BODY)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.capsuleId").value(capsule.getId()))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.content").value("내 편지"))
        .andExpect(jsonPath("$.data.themeType").value("WATERCOLOR"));
    Letter saved = letters.findAll().getFirst();
    assertThat(saved.getSubmittedAt()).isNull();
    assertThat(saved.getAuthor().getId()).isEqualTo(owner.getId());
    read(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.letterId").value(saved.getId()));
  }

  @Test
  void requiresAuthenticationForBothEndpoints() throws Exception {
    mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(path() + "/me")).andExpect(status().isUnauthorized());
    assertThat(letters.count()).isZero();
  }

  @Test
  void duplicateDoesNotOverwrite() throws Exception {
    create(token, BODY).andExpect(status().isCreated());
    create(token, "{\"content\":\"덮어쓰기\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LETTER_ALREADY_EXISTS"));
    assertThat(letters.count()).isEqualTo(1);
    assertThat(letters.findAll().getFirst().getContent()).isEqualTo("내 편지");
  }

  @Test
  void missingOwnLetterDoesNotExposeAnotherAuthorsLetter() throws Exception {
    create(token, BODY).andExpect(status().isCreated());
    User other = users.save(User.builder().kakaoId("other").nickname("다른 사람").build());
    join(other);
    read(tokens.generateToken(other, Duration.ofHours(1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LETTER_NOT_FOUND"));
  }

  @Test
  void nonMemberCannotCreateOrRead() throws Exception {
    User stranger = users.save(User.builder().kakaoId("stranger").nickname("외부인").build());
    String strangerToken = tokens.generateToken(stranger, Duration.ofHours(1));
    create(strangerToken, BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    read(strangerToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    assertThat(letters.count()).isZero();
  }

  @Test
  void inactiveMemberCannotAccessExistingLetter() throws Exception {
    create(token, BODY).andExpect(status().isCreated());
    for (String memberStatus : List.of("LEFT", "REMOVED")) {
      jdbc.update(
          "update capsule_members set status = ? where time_capsule_id = ?",
          memberStatus,
          capsule.getId());
      create(token, BODY)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
      read(token)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    }
    assertThat(letters.count()).isEqualTo(1);
  }

  @Test
  void missingAndDeletedCapsulesReturnSameError() throws Exception {
    mvc.perform(
            post("/v1/time-capsules/9223372036854775807/letters")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    mvc.perform(
            get("/v1/time-capsules/9223372036854775807/letters/me")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    create(token, BODY).andExpect(status().isCreated());
    jdbc.update(
        "update time_capsules set deleted_at = ? where id = ?",
        LocalDateTime.now(),
        capsule.getId());
    create(token, BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    read(token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    assertThat(letters.count()).isEqualTo(1);
  }

  @Test
  void deletedLetterIsAbsentAndCanBeRecreated() throws Exception {
    Letter deleted =
        letters.save(
            Letter.builder()
                .timeCapsule(capsule)
                .author(owner)
                .content("삭제됨")
                .deletedAt(LocalDateTime.now())
                .build());
    read(token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LETTER_NOT_FOUND"));
    create(token, BODY).andExpect(status().isCreated());
    assertThat(letters.count()).isEqualTo(2);
    assertThat(
            letters
                .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsule.getId(), owner.getId())
                .orElseThrow()
                .getId())
        .isNotEqualTo(deleted.getId());
  }

  @Test
  void malformedRequestsReturn400WithoutSaving() throws Exception {
    create(token, "{")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    mvc.perform(
            get("/v1/time-capsules/invalid/letters/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertThat(letters.count()).isZero();
  }

  @Test
  void concurrentRequestsCommitExactlyOneLetter() throws Exception {
    int count = 6;
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(count)) {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 시간 초과");
                  }
                  return create(token, BODY).andReturn().getResponse().getStatus();
                }));
      }
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get(20, TimeUnit.SECONDS));
      }
      assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409, 409);
    }
    assertThat(letters.count()).isEqualTo(1);
  }

  private void join(User user) {
    members.save(
        CapsuleMember.builder().timeCapsule(capsule).user(user).role(MemberRole.OWNER).build());
  }

  @ParameterizedTest
  @EnumSource(MemberRole.class)
  void allActiveRolesCanWrite(MemberRole role) throws Exception {
    jdbc.update(
        "update capsule_members set role = ? where time_capsule_id = ?",
        role.name(),
        capsule.getId());
    create(token, BODY).andExpect(status().isCreated());
    assertThat(letters.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"1,,201", "0,,409", "-1,,409", "60,1,201", "60,0,409", "60,-1,409", "0,60,409"})
  void creationHonorsExactTimeBoundaries(long openOffset, Long deadlineOffset, int expectedStatus)
      throws Exception {
    LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    jdbc.update(
        "update time_capsules set open_at = ?, letter_deadline_at = ? where id = ?",
        now.plusSeconds(openOffset),
        deadlineOffset == null ? null : now.plusSeconds(deadlineOffset),
        capsule.getId());
    ResultActions result = create(token, BODY).andExpect(status().is(expectedStatus));
    if (expectedStatus == 409) {
      result.andExpect(jsonPath("$.code").value("LETTER_WRITING_CLOSED"));
      assertThat(letters.count()).isZero();
    } else {
      assertThat(letters.count()).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = CapsuleStatus.class,
      names = {"LOCKED", "OPENED"})
  void nonWritingStatesRejectCreationEvenBeforeDeadline(CapsuleStatus capsuleStatus)
      throws Exception {
    jdbc.update(
        "update time_capsules set status = ? where id = ?", capsuleStatus.name(), capsule.getId());
    create(token, BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LETTER_WRITING_CLOSED"));
    assertThat(letters.count()).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    "WRITING,DRAFT",
    "LOCKED,DRAFT",
    "OPENED,DRAFT",
    "WRITING,SUBMITTED",
    "LOCKED,SUBMITTED",
    "OPENED,SUBMITTED"
  })
  void ownLetterRemainsReadableAfterDeadline(CapsuleStatus capsuleStatus, LetterStatus letterStatus)
      throws Exception {
    Letter letter =
        letters.save(
            Letter.builder()
                .timeCapsule(capsule)
                .author(owner)
                .content("본인 편지")
                .status(letterStatus)
                .submittedAt(letterStatus == LetterStatus.SUBMITTED ? LocalDateTime.now() : null)
                .build());
    LocalDateTime past = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1);
    jdbc.update(
        "update time_capsules set status = ?, open_at = ?, letter_deadline_at = ? where id = ?",
        capsuleStatus.name(),
        past,
        past,
        capsule.getId());
    read(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.letterId").value(letter.getId()))
        .andExpect(jsonPath("$.data.status").value(letterStatus.name()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"content\":null}"})
  void absentOrNullContentIsRejected(String body) throws Exception {
    create(token, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    assertThat(letters.count()).isZero();
  }

  @Test
  void emptyDraftAndMissingThemeAreAllowed() throws Exception {
    create(token, "{\"content\":\"\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.content").value(""));
    Letter saved = letters.findAll().getFirst();
    assertThat(saved.getContent()).isEmpty();
    assertThat(saved.getThemeType()).isNull();
  }

  @Test
  void acceptsMaximumContentAndThemeLengths() throws Exception {
    String content = "가".repeat(10000);
    String theme = "T".repeat(20);
    create(
            token,
            objectMapper.writeValueAsString(
                java.util.Map.of("content", content, "themeType", theme)))
        .andExpect(status().isCreated());
    Letter saved = letters.findAll().getFirst();
    assertThat(saved.getContent()).isEqualTo(content);
    assertThat(saved.getThemeType()).isEqualTo(theme);
  }

  @ParameterizedTest
  @CsvSource({"10001,20", "10000,21"})
  void rejectsLengthsAboveLimits(int contentLength, int themeLength) throws Exception {
    create(
            token,
            objectMapper.writeValueAsString(
                java.util.Map.of(
                    "content", "가".repeat(contentLength), "themeType", "T".repeat(themeLength))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    assertThat(letters.count()).isZero();
  }

  @Test
  void deletedLetterCannotBeRecreatedAfterDeadline() throws Exception {
    letters.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(owner)
            .content("삭제된 편지")
            .deletedAt(LocalDateTime.now())
            .build());
    jdbc.update(
        "update time_capsules set open_at = ? where id = ?",
        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
        capsule.getId());
    create(token, BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LETTER_WRITING_CLOSED"));
    assertThat(letters.count()).isEqualTo(1);
    assertThat(
            letters.findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                capsule.getId(), owner.getId()))
        .isEmpty();
  }

  private String path() {
    return "/v1/time-capsules/" + capsule.getId() + "/letters";
  }

  private ResultActions create(String accessToken, String body) throws Exception {
    return mvc.perform(
        post(path())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions read(String accessToken) throws Exception {
    return mvc.perform(get(path() + "/me").header("Authorization", "Bearer " + accessToken));
  }
}
