package com.momento.server.domain.user;

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
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
