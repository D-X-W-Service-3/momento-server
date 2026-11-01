package com.momento.server.domain.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class PublicLetterIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TokenProvider tokens;
  @Autowired private UserRepository users;
  @Autowired private TimeCapsuleRepository capsules;
  @Autowired private CapsuleMemberRepository members;
  @Autowired private LetterRepository letters;
  @Autowired private JdbcTemplate jdbc;
  private User owner;
  private User recipient;
  private User participant;
  private TimeCapsule capsule;

  @BeforeEach
  void setUp() {
    clean();
    owner = user("owner");
    recipient = user("recipient");
    participant = user("participant");
    capsule = capsule(VisibilityType.ALL_MEMBERS);
    join(owner, MemberRole.OWNER);
    join(recipient, MemberRole.RECIPIENT);
    join(participant, MemberRole.PARTICIPANT);
  }

  @AfterEach
  void clean() {
    letters.deleteAll();
    members.deleteAll();
    capsules.deleteAll();
    users.deleteAll();
  }

  @ParameterizedTest
  @CsvSource({
    "ALL_MEMBERS,OWNER,3",
    "ALL_MEMBERS,RECIPIENT,3",
    "ALL_MEMBERS,PARTICIPANT,3",
    "RECIPIENT_AND_AUTHOR,OWNER,1",
    "RECIPIENT_AND_AUTHOR,RECIPIENT,3",
    "RECIPIENT_AND_AUTHOR,PARTICIPANT,1"
  })
  void filtersByRoleAndVisibility(VisibilityType visibility, MemberRole role, int count)
      throws Exception {
    jdbc.update(
        "update time_capsules set visibility_type = ? where id = ?",
        visibility.name(),
        capsule.getId());
    Letter own = letter(owner, LetterStatus.SUBMITTED);
    letter(recipient, LetterStatus.SUBMITTED);
    Letter other = letter(participant, LetterStatus.SUBMITTED);
    User viewer =
        switch (role) {
          case OWNER -> owner;
          case RECIPIENT -> recipient;
          case PARTICIPANT -> participant;
        };
    ResultActions result =
        read(viewer)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.letters.length()").value(count));
    if (count == 1) {
      result.andExpect(
          jsonPath("$.data.letters[0].letterId")
              .value(role == MemberRole.OWNER ? own.getId() : other.getId()));
    }
  }

  @Test
  void sortsByCreationThenIdAndReturnsExactContract() throws Exception {
    Letter first = letter(owner, LetterStatus.SUBMITTED);
    Letter second = letter(participant, LetterStatus.SUBMITTED);
    Letter oldest = letter(recipient, LetterStatus.SUBMITTED);
    LocalDateTime same = LocalDateTime.of(2027, 3, 1, 21, 0);
    jdbc.update(
        "update letters set created_at = ? where id in (?, ?)",
        same,
        first.getId(),
        second.getId());
    jdbc.update(
        "update letters set created_at = ? where id = ?", same.minusDays(1), oldest.getId());
    var result =
        read(owner)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.letters[0].letterId").value(oldest.getId()))
            .andExpect(jsonPath("$.data.letters[1].letterId").value(first.getId()))
            .andExpect(jsonPath("$.data.letters[2].letterId").value(second.getId()))
            .andExpect(jsonPath("$.data.letters[1].themeType").value("COZY"))
            .andExpect(jsonPath("$.data.letters[1].title").value("제목"))
            .andExpect(jsonPath("$.data.letters[1].content").value("내용"))
            .andExpect(jsonPath("$.data.letters[1].authorNickname").value(owner.getNickname()))
            .andExpect(jsonPath("$.data.letters[1].createdAt").value("2027-03-01T21:00:00"))
            .andReturn();
    var item =
        mapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("letters")
            .get(1);
    assertThat(item.size()).isEqualTo(7);
    assertThat(item.has("imageUrl")).isTrue();
    assertThat(item.path("imageUrl").isNull()).isTrue();
  }

  @Test
  void excludesDraftDeletedAndOtherCapsuleLetters() throws Exception {
    letter(owner, LetterStatus.DRAFT);
    Letter deleted = letter(participant, LetterStatus.SUBMITTED);
    jdbc.update(
        "update letters set deleted_at = ? where id = ?", LocalDateTime.now(), deleted.getId());
    letters.save(
        Letter.builder()
            .timeCapsule(capsule(VisibilityType.ALL_MEMBERS))
            .author(owner)
            .content("다른 캡슐")
            .status(LetterStatus.SUBMITTED)
            .build());
    read(owner).andExpect(status().isOk()).andExpect(jsonPath("$.data.letters").isEmpty());
  }

  @Test
  void restrictedMemberWithoutOwnLetterReceivesEmptyList() throws Exception {
    jdbc.update(
        "update time_capsules set visibility_type = 'RECIPIENT_AND_AUTHOR' where id = ?",
        capsule.getId());
    letter(owner, LetterStatus.SUBMITTED);
    read(participant).andExpect(status().isOk()).andExpect(jsonPath("$.data.letters").isEmpty());
  }

  @Test
  void preservesLegacyNullFields() throws Exception {
    Letter legacy =
        letters.save(
            Letter.builder()
                .timeCapsule(capsule)
                .author(owner)
                .content("기존 내용")
                .status(LetterStatus.SUBMITTED)
                .build());
    var result = read(owner).andExpect(status().isOk()).andReturn();
    var item =
        mapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("letters")
            .get(0);
    assertThat(item.path("letterId").asLong()).isEqualTo(legacy.getId());
    for (String field : new String[] {"title", "themeType", "imageUrl"}) {
      assertThat(item.has(field)).isTrue();
      assertThat(item.path(field).isNull()).isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = CapsuleStatus.class,
      names = {"WRITING", "LOCKED"})
  void rejectsUnopenedEvenAfterOpenTime(CapsuleStatus state) throws Exception {
    jdbc.update(
        "update time_capsules set status = ?, open_at = ? where id = ?",
        state.name(),
        LocalDateTime.now().minusDays(1),
        capsule.getId());
    read(owner)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_OPENED"))
        .andExpect(jsonPath("$.message").value("아직 공개되지 않은 타임캡슐입니다."));
  }

  @ParameterizedTest
  @CsvSource({"OPENED", "WRITING", "LOCKED"})
  void nonMemberCannotDiscoverState(String state) throws Exception {
    jdbc.update("update time_capsules set status = ? where id = ?", state, capsule.getId());
    read(user("outsider"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @ParameterizedTest
  @CsvSource({"LEFT", "REMOVED"})
  void rejectsInactiveMember(String state) throws Exception {
    jdbc.update(
        "update capsule_members set status = ? where time_capsule_id = ? and user_id = ?",
        state,
        capsule.getId(),
        owner.getId());
    read(owner)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  void rejectsDeletedAndMissingCapsules() throws Exception {
    jdbc.update(
        "update time_capsules set deleted_at = ? where id = ?",
        LocalDateTime.now(),
        capsule.getId());
    read(owner)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
    mvc.perform(
            get("/v1/time-capsules/999999/letters")
                .header("Authorization", "Bearer " + token(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAPSULE_NOT_FOUND"));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    jdbc.update("update users set deleted_at = ? where id = ?", LocalDateTime.now(), owner.getId());
    read(owner).andExpect(status().isUnauthorized());
  }

  private User user(String name) {
    return users.save(User.builder().kakaoId("public-" + name).nickname("public-" + name).build());
  }

  private TimeCapsule capsule(VisibilityType visibility) {
    return capsules.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("COZY")
            .title("공개 캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(visibility)
            .status(CapsuleStatus.OPENED)
            .letterDeadlineAt(LocalDateTime.now().minusDays(2))
            .openAt(LocalDateTime.now().minusDays(1))
            .build());
  }

  private void join(User user, MemberRole role) {
    members.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .joinedAt(LocalDateTime.now())
            .build());
  }

  private Letter letter(User author, LetterStatus state) {
    return letters.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .title("제목")
            .content("내용")
            .themeType("COZY")
            .status(state)
            .build());
  }

  private String path() {
    return "/v1/time-capsules/" + capsule.getId() + "/letters";
  }

  private String token(User user) {
    return tokens.generateToken(user, Duration.ofHours(1));
  }

  private ResultActions read(User user) throws Exception {
    return mvc.perform(get(path()).header("Authorization", "Bearer " + token(user)));
  }
}
