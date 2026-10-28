package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.letter.service.LetterService;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import com.momento.server.global.common.exception.ApiException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 방장이 캡슐을 고치고 지우는 API 를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeCapsuleManageIntegrationTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private LetterService letterService;
  @Autowired private TimeCapsuleService timeCapsuleService;

  private User owner;
  private User participant;
  private TimeCapsule capsule;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("manage-owner", "방장");
    participant = saveUser("manage-participant", "참여자");
    capsule = saveCapsule(CapsuleStatus.WRITING);
    join(capsule, owner, MemberRole.OWNER);
    join(capsule, participant, MemberRole.PARTICIPANT);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  // ---------- 수정 ----------

  @Test
  @DisplayName("보낸 필드만 바꾸고 응답 데이터는 없다")
  void updatesOnlySentFields() throws Exception {
    update(owner, "{\"title\": \"  제주 여행  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());

    TimeCapsule saved = reload();
    assertThat(saved.getTitle()).isEqualTo("제주 여행");
    assertThat(saved.getIconType()).isEqualTo("ICE_CREAM");
    assertThat(saved.getDescription()).isEqualTo("설명");
  }

  @Test
  @DisplayName("아이콘 · 이름 · 설명을 한 번에 바꾼다")
  void updatesAllFields() throws Exception {
    update(owner, "{\"iconType\": \"CAKE\", \"title\": \"새 이름\", \"description\": \"새 설명\"}")
        .andExpect(status().isOk());

    TimeCapsule saved = reload();
    assertThat(saved.getIconType()).isEqualTo("CAKE");
    assertThat(saved.getTitle()).isEqualTo("새 이름");
    assertThat(saved.getDescription()).isEqualTo("새 설명");
  }

  @Test
  @DisplayName("설명에 빈 문자열을 보내면 지우고, null 을 보내면 그대로 둔다")
  void clearsDescriptionOnlyWithBlank() throws Exception {
    update(owner, "{\"description\": null}").andExpect(status().isOk());
    assertThat(reload().getDescription()).isEqualTo("설명");

    update(owner, "{\"description\": \"  \"}").andExpect(status().isOk());
    assertThat(reload().getDescription()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "{\"title\": \"\"}",
        "{\"iconType\": \" \"}",
        "{\"title\": \"가나다라마바사아자차카타파하가나다라마바사\"}",
        "{\"description\": \"가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라\"}"
      })
  @DisplayName("빈 이름 · 빈 아이콘 · 21자 이름 · 46자 설명은 400 이고 아무것도 바꾸지 않는다")
  void rejectsInvalidInput(String body) throws Exception {
    update(owner, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

    assertThat(reload().getTitle()).isEqualTo("캡슐");
    assertThat(reload().getDescription()).isEqualTo("설명");
  }

  @Test
  @DisplayName("방장이 아니면 403 이다")
  void participantCannotUpdate() throws Exception {
    update(participant, "{\"title\": \"몰래\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));

    assertThat(reload().getTitle()).isEqualTo("캡슐");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = CapsuleStatus.class,
      names = {"LOCKED", "OPENED"})
  @DisplayName("편지 수정 마감 뒤에는 409 다")
  void cannotUpdateAfterDeadline(CapsuleStatus closed) throws Exception {
    TimeCapsule closedCapsule = saveCapsule(closed);
    join(closedCapsule, owner, MemberRole.OWNER);

    update(owner, closedCapsule, "{\"title\": \"늦음\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("멤버가 아니면 방장 여부보다 먼저 404 다")
  void strangerGetsNotFound() throws Exception {
    update(saveUser("manage-stranger", "남"), "{\"title\": \"몰래\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 수정할 수 없다")
  void updateRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            patch("/v1/time-capsules/{capsuleId}", capsule.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"몰래\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ---------- 삭제 ----------

  @Test
  @DisplayName("편지가 없으면 지우고, 지운 캡슐은 상세 · 목록에서 사라진다")
  void deletesCapsuleWithoutLetters() throws Exception {
    delete(owner, capsule).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

    assertThat(reload().getDeletedAt()).isNotNull();
    mockMvc
        .perform(
            get("/v1/time-capsules/{capsuleId}", capsule.getId())
                .header(AUTHORIZATION, bearer(owner)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/v1/time-capsules").header(AUTHORIZATION, bearer(participant)))
        .andExpect(jsonPath("$.data.capsules", hasSize(0)));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(LetterStatus.class)
  @DisplayName("임시 저장이든 제출이든 편지가 한 통이라도 있으면 409 이고 지우지 않는다")
  void cannotDeleteWithLetters(LetterStatus letterStatus) throws Exception {
    saveLetter(participant, letterStatus, null);

    delete(owner, capsule)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_HAS_LETTERS"));

    assertThat(reload().getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("방장 본인의 편지도 센다")
  void ownerLetterCounts() throws Exception {
    saveLetter(owner, LetterStatus.DRAFT, null);

    delete(owner, capsule).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("삭제된 편지만 있으면 지울 수 있다")
  void deletedLettersDoNotCount() throws Exception {
    saveLetter(participant, LetterStatus.SUBMITTED, FAR_FUTURE);

    delete(owner, capsule).andExpect(status().isOk());
  }

  @Test
  @DisplayName("방장이 아니면 403 이다")
  void participantCannotDelete() throws Exception {
    delete(participant, capsule)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CAPSULE_OWNER_ONLY"));

    assertThat(reload().getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("편지 수정 마감 뒤에는 편지가 없어도 409 다")
  void cannotDeleteAfterDeadline() throws Exception {
    TimeCapsule locked = saveCapsule(CapsuleStatus.LOCKED);
    join(locked, owner, MemberRole.OWNER);

    delete(owner, locked)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_EDITABLE"));
  }

  @Test
  @DisplayName("멤버가 아니면 404 이고, 이미 지운 캡슐도 404 다")
  void deleteNotFound() throws Exception {
    delete(saveUser("manage-stranger", "남"), capsule).andExpect(status().isNotFound());

    delete(owner, capsule).andExpect(status().isOk());
    delete(owner, capsule)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  @DisplayName("인증 없이 지울 수 없다")
  void deleteRequiresAuthentication() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.delete("/v1/time-capsules/{capsuleId}", capsule.getId()))
        .andExpect(status().isUnauthorized());
  }

  @RepeatedTest(5)
  @DisplayName("삭제와 편지 작성이 동시에 들어와도 편지가 담긴 캡슐이 지워지지 않는다")
  void deleteAndWriteConcurrently() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> write =
          executor.submit(
              () -> {
                start.await();
                return letterService.create(
                    capsule.getId(), participant.getId(), new LetterCreateRequest("편지", null));
              });
      Future<?> remove =
          executor.submit(
              () -> {
                start.await();
                timeCapsuleService.delete(capsule.getId(), owner.getId());
                return null;
              });
      start.countDown();
      boolean written = succeeded(write);
      boolean deleted = succeeded(remove);

      assertThat(written ^ deleted).as("둘 중 하나만 성공한다").isTrue();
      assertThat(reload().getDeletedAt() != null).isEqualTo(deleted);
      assertThat(letterRepository.countByTimeCapsuleIdAndDeletedAtIsNull(capsule.getId()))
          .isEqualTo(written ? 1 : 0);
    } finally {
      executor.shutdownNow();
    }
  }

  /** 다른 쪽에 밀려 거절됐으면 false. 거절 사유는 409(편지가 있음) 또는 404(캡슐이 지워짐)다. */
  private boolean succeeded(Future<?> task) throws Exception {
    try {
      task.get(10, TimeUnit.SECONDS);
      return true;
    } catch (ExecutionException exception) {
      ApiException cause = (ApiException) exception.getCause();
      assertThat(cause.getErrorCode())
          .isIn(CapsuleErrorCode.CAPSULE_HAS_LETTERS, CapsuleErrorCode.CAPSULE_NOT_FOUND);
      return false;
    }
  }

  private ResultActions delete(User user, TimeCapsule target) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.delete("/v1/time-capsules/{capsuleId}", target.getId())
            .header(AUTHORIZATION, bearer(user)));
  }

  private void saveLetter(User author, LetterStatus letterStatus, LocalDateTime deletedAt) {
    letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("편지")
            .status(letterStatus)
            .deletedAt(deletedAt)
            .build());
  }

  private ResultActions update(User user, String body) throws Exception {
    return update(user, capsule, body);
  }

  private ResultActions update(User user, TimeCapsule target, String body) throws Exception {
    return mockMvc.perform(
        patch("/v1/time-capsules/{capsuleId}", target.getId())
            .header(AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String bearer(User user) {
    return "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private TimeCapsule reload() {
    return timeCapsuleRepository.findById(capsule.getId()).orElseThrow();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(CapsuleStatus status) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .description("설명")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(FAR_FUTURE.minusDays(10))
            .openAt(FAR_FUTURE)
            .build());
  }

  private CapsuleMember join(TimeCapsule target, User user, MemberRole role) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(target)
            .user(user)
            .role(role)
            .joinedAt(FAR_FUTURE.minusDays(30))
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
