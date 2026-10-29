package com.momento.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Duration;
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
 * 회원 탈퇴 API 를 검증한다. 같은 카카오 계정으로 다시 로그인했을 때의 복구는 {@code KakaoLoginIntegrationTest} 에서 본다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이는 테스트마다 직접 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWithdrawIntegrationTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;

  private User me;
  private String myToken;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("withdraw-me", "상래");
    myToken = tokenOf(me);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("탈퇴하면 회원 행에 탈퇴 일시가 채워지고 응답 데이터는 없다")
  void withdraws() throws Exception {
    withdraw(myToken).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

    User withdrawn = userRepository.findById(me.getId()).orElseThrow();
    assertThat(withdrawn.isWithdrawn()).isTrue();
    assertThat(withdrawn.getNickname()).isEqualTo("상래");
  }

  @Test
  @DisplayName("탈퇴한 뒤 같은 토큰으로 보낸 요청은 401 이다")
  void tokenIsRejectedAfterWithdrawal() throws Exception {
    withdraw(myToken).andExpect(status().isOk());

    perform(get("/v1/users/me"), myToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    withdraw(myToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("방장이 탈퇴해도 캡슐 · 멤버 · 편지는 그대로이고 다른 멤버는 캡슐을 계속 본다")
  void keepsCapsuleWhenOwnerWithdraws() throws Exception {
    User participant = saveUser("withdraw-participant", "유빈");
    TimeCapsule capsule = saveCapsule();
    CapsuleMember owner = join(capsule, me, MemberRole.OWNER);
    join(capsule, participant, MemberRole.PARTICIPANT);
    Letter letter = saveLetter(capsule, me);

    withdraw(myToken).andExpect(status().isOk());

    assertThat(timeCapsuleRepository.findById(capsule.getId()).orElseThrow().getDeletedAt())
        .isNull();
    assertThat(capsuleMemberRepository.findById(owner.getId()).orElseThrow().getStatus())
        .isEqualTo(MemberStatus.ACTIVE);
    assertThat(letterRepository.findById(letter.getId()).orElseThrow().getDeletedAt()).isNull();
    perform(get("/v1/time-capsules/{capsuleId}", capsule.getId()), tokenOf(participant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsuleId").value(capsule.getId()));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(delete("/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

    assertThat(userRepository.findById(me.getId()).orElseThrow().isWithdrawn()).isFalse();
  }

  private ResultActions withdraw(String token) throws Exception {
    return perform(delete("/v1/users/me"), token);
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, String token)
      throws Exception {
    return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + token));
  }

  private String tokenOf(User user) {
    return tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule() {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(CapsuleStatus.WRITING)
            .letterDeadlineAt(FAR_FUTURE.minusDays(10))
            .openAt(FAR_FUTURE)
            .build());
  }

  private CapsuleMember join(TimeCapsule capsule, User user, MemberRole role) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(MemberStatus.ACTIVE)
            .joinedAt(FAR_FUTURE.minusDays(30))
            .build());
  }

  private Letter saveLetter(TimeCapsule capsule, User author) {
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("편지")
            .status(LetterStatus.SUBMITTED)
            .submittedAt(FAR_FUTURE.minusDays(20))
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
