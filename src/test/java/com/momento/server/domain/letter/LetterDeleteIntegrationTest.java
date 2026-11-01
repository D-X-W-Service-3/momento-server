package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.timecapsule.service.CapsuleMemberService;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
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
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class LetterDeleteIntegrationTest {
  private static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");
  @Autowired private MockMvc mvc;
  @Autowired private TokenProvider tokens;
  @Autowired private UserRepository users;
  @Autowired private TimeCapsuleRepository capsules;
  @Autowired private CapsuleMemberRepository members;
  @Autowired private LetterRepository letters;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private CapsuleMemberService memberService;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private TimeCapsuleService capsuleService;
  private User owner;
  private User author;
  private TimeCapsule capsule;
  private CapsuleMember authorMember;
  private String token;

  @BeforeEach
  void setUp() {
    clean();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    owner = users.save(User.builder().kakaoId("delete-owner").nickname("삭제방장").build());
    author = users.save(User.builder().kakaoId("delete-author").nickname("삭제작성자").build());
    capsule =
        capsules.save(
            TimeCapsule.builder()
                .creator(owner)
                .title("캡슐")
                .iconType("ICE_CREAM")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .letterDeadlineAt(now().plusDays(1))
                .openAt(now().plusDays(2))
                .build());
    members.save(
        CapsuleMember.builder().timeCapsule(capsule).user(owner).role(MemberRole.OWNER).build());
    authorMember =
        members.save(
            CapsuleMember.builder()
                .timeCapsule(capsule)
                .user(author)
                .role(MemberRole.PARTICIPANT)
                .build());
    token = tokens.generateToken(author, Duration.ofHours(1));
  }

  @AfterEach
  void clean() {
    letters.deleteAll();
    members.deleteAll();
    capsules.deleteAll();
    users.deleteAll();
  }

  @ParameterizedTest
  @EnumSource(LetterStatus.class)
  void softDeletionPreservesDataAndRemovesFromReadAndCounts(LetterStatus letterStatus)
      throws Exception {
    Letter letter = saveLetter(letterStatus);
    Letter before = letters.findById(letter.getId()).orElseThrow();
    read().andExpect(status().isOk()).andExpect(jsonPath("$.data.canDelete").value(true));
    remove(letter.getId(), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
    Letter saved = letters.findById(letter.getId()).orElseThrow();
    assertThat(saved.getDeletedAt()).isEqualTo(now());
    assertThat(saved.getTitle()).isEqualTo(before.getTitle());
    assertThat(saved.getContent()).isEqualTo(before.getContent());
    assertThat(saved.getThemeType()).isEqualTo(before.getThemeType());
    assertThat(saved.getStatus()).isEqualTo(letterStatus);
    assertThat(saved.getSubmittedAt()).isEqualTo(before.getSubmittedAt());
    assertThat(saved.getCreatedAt()).isEqualTo(before.getCreatedAt());
    assertThat(letters.count()).isEqualTo(1);
    assertThat(capsules.countLettersByStatus(capsule.getId(), LetterStatus.SUBMITTED)).isZero();
    assertThat(letters.countByTimeCapsuleIdAndDeletedAtIsNull(capsule.getId())).isZero();
    read().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LETTER_NOT_FOUND"));
  }

  @Test
  void foreignMissingAndDeletedIdsHaveIdentical404AndDoNotChangeData() throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    String ownerToken = tokens.generateToken(owner, Duration.ofHours(1));
    String foreign =
        remove(letter.getId(), ownerToken)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String missing =
        remove(Long.MAX_VALUE, token)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isNull();
    remove(letter.getId(), token).andExpect(status().isOk());
    LocalDateTime first = letters.findById(letter.getId()).orElseThrow().getDeletedAt();
    given(clock.instant()).willReturn(NOW.plusSeconds(30));
    String deleted =
        remove(letter.getId(), token)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(foreign).isEqualTo(missing).isEqualTo(deleted);
    assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isEqualTo(first);
  }

  @ParameterizedTest
  @CsvSource({
    "WRITING,60,1,200",
    "WRITING,60,0,409",
    "WRITING,60,-1,409",
    "WRITING,1,60,200",
    "WRITING,0,60,409",
    "WRITING,-1,60,409",
    "LOCKED,60,60,409",
    "OPENED,60,60,409"
  })
  void deletionHonorsPeriodBoundaries(
      String state, int openOffset, int deadlineOffset, int expected) throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    jdbc.update(
        "update time_capsules set status = ?, open_at = ?, letter_deadline_at = ? where id = ?",
        state,
        now().plusSeconds(openOffset),
        now().plusSeconds(deadlineOffset),
        capsule.getId());
    read()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.canDelete").value(expected == 200));
    ResultActions response = remove(letter.getId(), token).andExpect(status().is(expected));
    if (expected == 409) {
      response.andExpect(jsonPath("$.code").value("LETTER_WRITING_CLOSED"));
      assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isNull();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER,ALL_MEMBERS",
    "PARTICIPANT,ALL_MEMBERS",
    "RECIPIENT,ALL_MEMBERS",
    "OWNER,RECIPIENT_AND_AUTHOR",
    "PARTICIPANT,RECIPIENT_AND_AUTHOR",
    "RECIPIENT,RECIPIENT_AND_AUTHOR"
  })
  void allActiveAuthorsCanDeleteTheirLetter(MemberRole role, VisibilityType visibility)
      throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    jdbc.update(
        "update capsule_members set role = ? where id = ?", role.name(), authorMember.getId());
    jdbc.update(
        "update time_capsules set visibility_type = ? where id = ?",
        visibility.name(),
        capsule.getId());
    remove(letter.getId(), token).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"LEFT", "REMOVED", "ABSENT", "DELETED_CAPSULE"})
  void inaccessibleCapsuleIs404AndLetterUnchanged(String situation) throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    switch (situation) {
      case "ABSENT" -> members.deleteById(authorMember.getId());
      case "DELETED_CAPSULE" ->
          jdbc.update(
              "update time_capsules set deleted_at = ? where id = ?", now(), capsule.getId());
      default ->
          jdbc.update(
              "update capsule_members set status = ? where id = ?",
              situation,
              authorMember.getId());
    }
    remove(letter.getId(), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void authenticationAndPathValidationDoNotDelete() throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    mvc.perform(delete("/v1/letters/{id}", letter.getId())).andExpect(status().isUnauthorized());
    mvc.perform(delete("/v1/letters/invalid").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void recreateUsesNewIdAndOldDeleteCannotDeleteNewLetter() throws Exception {
    Letter old = saveLetter(LetterStatus.SUBMITTED);
    remove(old.getId(), token).andExpect(status().isOk());
    create().andExpect(status().isCreated());
    Letter replacement =
        letters
            .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsule.getId(), author.getId())
            .orElseThrow();
    assertThat(replacement.getId()).isNotEqualTo(old.getId());
    assertThat(replacement.getStatus()).isEqualTo(LetterStatus.SUBMITTED);
    LetterStatus replacementStatus = replacement.getStatus();
    remove(old.getId(), token).andExpect(status().isNotFound());
    assertThat(letters.findById(replacement.getId()).orElseThrow().getDeletedAt()).isNull();
    assertThat(letters.findById(replacement.getId()).orElseThrow().getStatus())
        .isEqualTo(replacementStatus);
    assertThat(letters.countByTimeCapsuleIdAndDeletedAtIsNull(capsule.getId())).isEqualTo(1);
  }

  @Test
  void simultaneousDeletesSucceedOnce() throws Exception {
    Letter letter = saveLetter(LetterStatus.SUBMITTED);
    try (var pool = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Future<Integer> first =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return remove(letter.getId(), token).andReturn().getResponse().getStatus();
              });
      Future<Integer> second =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return remove(letter.getId(), token).andReturn().getResponse().getStatus();
              });
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 404);
    }
    assertThat(letters.findById(letter.getId()).orElseThrow().getDeletedAt()).isEqualTo(now());
  }

  @RepeatedTest(3)
  void concurrentDeleteAndCreateNeverDeleteReplacementOrDuplicateActiveLetter() throws Exception {
    Letter old = saveLetter(LetterStatus.SUBMITTED);
    try (var pool = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Future<Integer> deletion =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return remove(old.getId(), token).andReturn().getResponse().getStatus();
              });
      Future<Integer> creation =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return create().andReturn().getResponse().getStatus();
              });
      start.countDown();
      assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(200);
      int created = creation.get(10, TimeUnit.SECONDS);
      assertThat(created).isIn(201, 409);
      var active =
          letters.findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsule.getId(), author.getId());
      assertThat(active.isPresent()).isEqualTo(created == 201);
      active.ifPresent(letter -> assertThat(letter.getId()).isNotEqualTo(old.getId()));
    }
    assertThat(letters.findById(old.getId()).orElseThrow().getDeletedAt()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"deadline", "delete", "delete-recreate", "remove-member", "delete-capsule"})
  void rechecksStateAfterWaitingForCapsuleLock(String change) throws Exception {
    Letter old = saveLetter(LetterStatus.SUBMITTED);
    CountDownLatch reachedLock = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              reachedLock.countDown();
              return invocation.callRealMethod();
            })
        .when(capsuleService)
        .getActiveCapsuleForUpdate(capsule.getId());
    try (var pool = Executors.newSingleThreadExecutor()) {
      List<Future<Integer>> pending = new ArrayList<>();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              tx -> {
                capsules.findActiveByIdForUpdate(capsule.getId()).orElseThrow();
                Future<Integer> result =
                    pool.submit(
                        () -> remove(old.getId(), token).andReturn().getResponse().getStatus());
                pending.add(result);
                try {
                  assertThat(reachedLock.await(5, TimeUnit.SECONDS)).isTrue();
                  assertThatThrownBy(() -> result.get(200, TimeUnit.MILLISECONDS))
                      .isInstanceOf(TimeoutException.class);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
                switch (change) {
                  case "deadline" -> given(clock.instant()).willReturn(NOW.plusSeconds(86400));
                  case "remove-member" ->
                      memberService.remove(capsule.getId(), owner.getId(), authorMember.getId());
                  case "delete-capsule" ->
                      jdbc.update(
                          "update time_capsules set deleted_at = ? where id = ?",
                          now(),
                          capsule.getId());
                  default -> {
                    jdbc.update(
                        "update letters set deleted_at = ? where id = ?",
                        now().minusMinutes(1),
                        old.getId());
                    if (change.equals("delete-recreate")) {
                      saveLetter(LetterStatus.SUBMITTED);
                    }
                  }
                }
              });
      assertThat(pending.getFirst().get(10, TimeUnit.SECONDS))
          .isEqualTo(change.equals("deadline") ? 409 : 404);
    }
    if (change.equals("deadline") || change.equals("delete-capsule")) {
      assertThat(letters.findById(old.getId()).orElseThrow().getDeletedAt()).isNull();
    }
    if (change.equals("delete") || change.equals("delete-recreate")) {
      assertThat(letters.findById(old.getId()).orElseThrow().getDeletedAt())
          .isEqualTo(now().minusMinutes(1));
    }
    if (change.equals("delete-recreate")) {
      assertThat(
              letters.findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                  capsule.getId(), author.getId()))
          .isPresent();
    }
    if (change.equals("remove-member")) {
      assertThat(members.findById(authorMember.getId()).orElseThrow().getStatus())
          .isEqualTo(MemberStatus.REMOVED);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "OWNER,ALL_MEMBERS", "PARTICIPANT,ALL_MEMBERS", "RECIPIENT,ALL_MEMBERS",
    "OWNER,RECIPIENT_AND_AUTHOR", "PARTICIPANT,RECIPIENT_AND_AUTHOR",
        "RECIPIENT,RECIPIENT_AND_AUTHOR"
  })
  void writesReadsDeletesAndRecreatesSubmittedLetterThroughApis(
      MemberRole role, VisibilityType visibility) throws Exception {
    jdbc.update(
        "update capsule_members set role = ? where id = ?", role.name(), authorMember.getId());
    jdbc.update(
        "update time_capsules set visibility_type = ? where id = ?",
        visibility.name(),
        capsule.getId());
    if (role == MemberRole.RECIPIENT && visibility == VisibilityType.RECIPIENT_AND_AUTHOR) {
      create()
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("LETTER_WRITING_NOT_ALLOWED"));
      assertThat(letters.count()).isZero();
      return;
    }
    create().andExpect(status().isCreated()).andExpect(jsonPath("$.data").doesNotExist());
    Letter first =
        letters
            .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsule.getId(), author.getId())
            .orElseThrow();
    assertThat(first.getStatus()).isEqualTo(LetterStatus.SUBMITTED);
    assertThat(first.getSubmittedAt()).isEqualTo(now());
    read()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.letterId").value(first.getId()))
        .andExpect(jsonPath("$.data.title").value("다시 작성"))
        .andExpect(jsonPath("$.data.content").value("새 본문"))
        .andExpect(jsonPath("$.data.themeType").value("COZY"))
        .andExpect(jsonPath("$.data.createdAt").exists())
        .andExpect(jsonPath("$.data.canDelete").value(true))
        .andExpect(jsonPath("$.data.status").doesNotExist())
        .andExpect(jsonPath("$.data.capsuleId").doesNotExist());
    remove(first.getId(), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
    read().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LETTER_NOT_FOUND"));
    assertThat(capsules.countLettersByStatus(capsule.getId(), LetterStatus.SUBMITTED)).isZero();
    given(clock.instant()).willReturn(NOW.plusSeconds(10));
    create().andExpect(status().isCreated());
    Letter replacement =
        letters
            .findByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(capsule.getId(), author.getId())
            .orElseThrow();
    assertThat(replacement.getId()).isNotEqualTo(first.getId());
    assertThat(replacement.getStatus()).isEqualTo(LetterStatus.SUBMITTED);
    assertThat(replacement.getSubmittedAt()).isEqualTo(now().plusSeconds(10));
    remove(first.getId(), token).andExpect(status().isNotFound());
    read()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.letterId").value(replacement.getId()))
        .andExpect(jsonPath("$.data.canDelete").value(true));
    assertThat(letters.findById(replacement.getId()).orElseThrow().getDeletedAt()).isNull();
    Letter deleted = letters.findById(first.getId()).orElseThrow();
    assertThat(deleted.getDeletedAt()).isEqualTo(now());
    assertThat(deleted.getSubmittedAt()).isEqualTo(now());
    assertThat(letters.count()).isEqualTo(2);
    assertThat(letters.countByTimeCapsuleIdAndDeletedAtIsNull(capsule.getId())).isEqualTo(1);
    assertThat(capsules.countLettersByStatus(capsule.getId(), LetterStatus.SUBMITTED)).isEqualTo(1);
  }

  private Letter saveLetter(LetterStatus status) {
    return letters.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("본문")
            .themeType("COZY")
            .status(status)
            .submittedAt(status == LetterStatus.SUBMITTED ? now().minusHours(1) : null)
            .build());
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  }

  private ResultActions remove(Long id, String accessToken) throws Exception {
    return mvc.perform(
        delete("/v1/letters/{id}", id).header("Authorization", "Bearer " + accessToken));
  }

  private ResultActions read() throws Exception {
    return mvc.perform(
        get("/v1/time-capsules/{id}/letters/me", capsule.getId())
            .header("Authorization", "Bearer " + token));
  }

  private ResultActions create() throws Exception {
    return mvc.perform(
        post("/v1/time-capsules/{id}/letters", capsule.getId())
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"다시 작성\",\"content\":\"새 본문\",\"themeType\":\"COZY\"}"));
  }
}
