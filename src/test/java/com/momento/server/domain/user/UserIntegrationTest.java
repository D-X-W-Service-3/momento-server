package com.momento.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.auth.service.TokenProvider;
import com.momento.server.global.common.exception.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 내 정보 API 를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserIntegrationTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private UserService userService;

  private User me;

  @BeforeEach
  void setUp() {
    cleanUp();
    me =
        userRepository.save(
            User.builder()
                .kakaoId("user-me")
                .nickname("상래")
                .profileImageUrl("https://k.kakaocdn.net/dn/profile.jpg")
                .birthday(LocalDate.of(2001, 3, 14))
                .email("sangrae@example.com")
                .phoneNumber("010-1234-5678")
                .build());
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("내 프로필을 조회한다")
  void getsMyProfile() throws Exception {
    authorized(get("/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(me.getId()))
        .andExpect(jsonPath("$.data.nickname").value("상래"))
        .andExpect(
            jsonPath("$.data.profileImageUrl").value("https://k.kakaocdn.net/dn/profile.jpg"))
        .andExpect(jsonPath("$.data.birthday").value("2001-03-14"))
        .andExpect(jsonPath("$.data.email").value("sangrae@example.com"))
        .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
        .andExpect(jsonPath("$.data.gender").doesNotExist());
  }

  @Test
  @DisplayName("입력하지 않은 프로필 필드는 null 이다")
  void emptyProfileFields() throws Exception {
    User fresh = userRepository.save(User.builder().kakaoId("user-fresh").nickname("새회원").build());

    authorized(get("/v1/users/me"), fresh)
        .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.birthday").value(nullValue()))
        .andExpect(jsonPath("$.data.email").value(nullValue()))
        .andExpect(jsonPath("$.data.phoneNumber").value(nullValue()));
  }

  @Test
  @DisplayName("활동 요약은 내가 속한 삭제되지 않은 캡슐을 상태별로 센다")
  void countsActivity() throws Exception {
    join(saveCapsule(CapsuleStatus.WRITING, null), MemberStatus.ACTIVE);
    join(saveCapsule(CapsuleStatus.WRITING, null), MemberStatus.ACTIVE);
    join(saveCapsule(CapsuleStatus.LOCKED, null), MemberStatus.ACTIVE);
    join(saveCapsule(CapsuleStatus.OPENED, null), MemberStatus.ACTIVE);
    join(saveCapsule(CapsuleStatus.OPENED, null), MemberStatus.REMOVED);
    join(saveCapsule(CapsuleStatus.WRITING, FAR_FUTURE), MemberStatus.ACTIVE);

    authorized(get("/v1/users/me"))
        .andExpect(jsonPath("$.data.activity.totalCapsuleCount").value(4))
        .andExpect(jsonPath("$.data.activity.beforeOpenCapsuleCount").value(3))
        .andExpect(jsonPath("$.data.activity.openedCapsuleCount").value(1))
        .andExpect(jsonPath("$.data.activity.letterCount").doesNotExist());
  }

  @Test
  @DisplayName("캡슐이 없으면 활동 요약은 모두 0 이다")
  void emptyActivity() throws Exception {
    authorized(get("/v1/users/me"))
        .andExpect(jsonPath("$.data.activity.totalCapsuleCount").value(0))
        .andExpect(jsonPath("$.data.activity.beforeOpenCapsuleCount").value(0))
        .andExpect(jsonPath("$.data.activity.openedCapsuleCount").value(0));
  }

  @Test
  @DisplayName("인증 없이 조회할 수 없다")
  void getRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("보낸 필드만 바꾸고, 수정 후 조회와 같은 형태를 돌려준다")
  void updatesOnlySentFields() throws Exception {
    join(saveCapsule(CapsuleStatus.WRITING, null), MemberStatus.ACTIVE);

    patch("{\"nickname\": \"  모모  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("모모"))
        .andExpect(jsonPath("$.data.birthday").value("2001-03-14"))
        .andExpect(jsonPath("$.data.email").value("sangrae@example.com"))
        .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
        .andExpect(jsonPath("$.data.activity.totalCapsuleCount").value(1));

    User saved = userRepository.findById(me.getId()).orElseThrow();
    assertThat(saved.getNickname()).isEqualTo("모모");
    assertThat(saved.getEmail()).isEqualTo("sangrae@example.com");
  }

  @Test
  @DisplayName("생일 · 이메일 · 전화번호를 바꾼다")
  void updatesProfileFields() throws Exception {
    patch(
            "{\"birthday\": \"1999-12-31\", \"email\": \"momo@example.com\","
                + " \"phoneNumber\": \"010-9876-5432\"}")
        .andExpect(status().isOk());

    User saved = userRepository.findById(me.getId()).orElseThrow();
    assertThat(saved.getBirthday()).isEqualTo(LocalDate.of(1999, 12, 31));
    assertThat(saved.getEmail()).isEqualTo("momo@example.com");
    assertThat(saved.getPhoneNumber()).isEqualTo("010-9876-5432");
    assertThat(saved.getNickname()).isEqualTo("상래");
  }

  @Test
  @DisplayName("이메일 · 전화번호에 빈 문자열을 보내면 지운다")
  void blankClearsEmailAndPhone() throws Exception {
    patch("{\"email\": \"\", \"phoneNumber\": \" \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(nullValue()))
        .andExpect(jsonPath("$.data.phoneNumber").value(nullValue()))
        .andExpect(jsonPath("$.data.birthday").value("2001-03-14"));
  }

  @Test
  @DisplayName("null 을 보낸 필드는 지우지 않고 그대로 둔다")
  void nullKeepsField() throws Exception {
    patch("{\"email\": null, \"birthday\": null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("sangrae@example.com"))
        .andExpect(jsonPath("$.data.birthday").value("2001-03-14"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"nickname\": \"상\"}|닉네임은 2자 이상 20자 이하여야 합니다.",
        "{\"nickname\": \"가나다라마바사아자차카타파하가나다라마바사\"}|닉네임은 2자 이상 20자 이하여야 합니다.",
        "{\"email\": \"sangrae\"}|이메일 형식이 올바르지 않습니다.",
        "{\"phoneNumber\": \"01012345678\"}|전화번호는 010-1234-5678 형식이어야 합니다."
      })
  @DisplayName("형식이 맞지 않으면 400 이고 아무것도 바꾸지 않는다")
  void rejectsInvalidInput(String body, String message) throws Exception {
    patch(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value(message));

    User saved = userRepository.findById(me.getId()).orElseThrow();
    assertThat(saved.getNickname()).isEqualTo("상래");
    assertThat(saved.getEmail()).isEqualTo("sangrae@example.com");
  }

  @Test
  @DisplayName("다른 회원이 쓰는 닉네임이면 409 다. 탈퇴한 회원의 닉네임도 쓸 수 없다")
  void rejectsTakenNickname() throws Exception {
    userRepository.save(User.builder().kakaoId("user-other").nickname("유빈").build());
    User withdrawn = User.builder().kakaoId("user-withdrawn").nickname("지수").build();
    withdrawn.withdraw();
    userRepository.save(withdrawn);

    patch("{\"nickname\": \"유빈\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    patch("{\"nickname\": \"지수\"}").andExpect(status().isConflict());

    assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("상래");
  }

  @Test
  @DisplayName("지금 내 닉네임을 그대로 보내면 성공이다")
  void acceptsMyCurrentNickname() throws Exception {
    patch("{\"nickname\": \"상래\"}").andExpect(status().isOk());
  }

  @Test
  @DisplayName("두 회원이 동시에 같은 닉네임으로 바꾸면 한 명만 성공하고 나머지는 409 다")
  void concurrentNicknameChange() throws Exception {
    User other = userRepository.save(User.builder().kakaoId("user-other").nickname("유빈").build());
    UserUpdateRequest request = new UserUpdateRequest("모모", null, null, null);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      List<Future<String>> results =
          List.of(me, other).stream()
              .map(
                  user ->
                      executor.submit(
                          () -> {
                            start.await();
                            try {
                              userService.updateProfile(user.getId(), request);
                              return "OK";
                            } catch (ApiException exception) {
                              return exception.getErrorCode().toString();
                            }
                          }))
              .toList();
      start.countDown();

      List<String> outcomes = new ArrayList<>();
      for (Future<String> result : results) {
        outcomes.add(result.get(10, TimeUnit.SECONDS));
      }
      assertThat(outcomes).containsExactlyInAnyOrder("OK", "NICKNAME_DUPLICATED");
    } finally {
      executor.shutdownNow();
    }
    assertThat(userRepository.findAll()).extracting(User::getNickname).containsOnlyOnce("모모");
  }

  @Test
  @DisplayName("인증 없이 수정할 수 없다")
  void patchRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"모모\"}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions patch(String body) throws Exception {
    return authorized(
        MockMvcRequestBuilders.patch("/v1/users/me")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions authorized(MockHttpServletRequestBuilder request) throws Exception {
    return authorized(request, me);
  }

  private ResultActions authorized(MockHttpServletRequestBuilder request, User user)
      throws Exception {
    return mockMvc.perform(
        request.header(
            AUTHORIZATION, "Bearer " + tokenProvider.generateToken(user, Duration.ofHours(1))));
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(FAR_FUTURE.minusDays(10))
            .openAt(FAR_FUTURE)
            .deletedAt(deletedAt)
            .build());
  }

  private void join(TimeCapsule capsule, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(me)
            .role(MemberRole.OWNER)
            .status(status)
            .joinedAt(FAR_FUTURE.minusDays(30))
            .build());
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
