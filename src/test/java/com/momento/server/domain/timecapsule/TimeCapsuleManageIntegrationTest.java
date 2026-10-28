package com.momento.server.domain.timecapsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
