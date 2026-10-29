package com.momento.server.domain.timecapsule.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.util.LikePattern;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 목록 조회 쿼리를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
class TimeCapsuleListQueryTest {

  private static final LocalDateTime OPEN = LocalDateTime.of(2045, 1, 1, 0, 0);
  private static final Limit ALL = Limit.of(100);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("list-me", "상래");
    friend = saveUser("list-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("내가 ACTIVE 멤버인 삭제되지 않은 캡슐만 나온다")
  void onlyMyActiveCapsules() {
    TimeCapsule mine = saveCapsule("내 캡슐", OPEN, CapsuleStatus.WRITING);
    join(mine, me, MemberStatus.ACTIVE);
    join(saveCapsule("남의 캡슐", OPEN, CapsuleStatus.WRITING), friend, MemberStatus.ACTIVE);
    join(saveCapsule("내보내진 캡슐", OPEN, CapsuleStatus.WRITING), me, MemberStatus.REMOVED);
    TimeCapsule deleted = saveCapsule("삭제된 캡슐", OPEN, CapsuleStatus.WRITING, OPEN);
    join(deleted, me, MemberStatus.ACTIVE);

    assertThat(ids(findAll(null, null))).containsExactly(mine.getId());
  }

  @Test
  @DisplayName("개봉일 오름차순이고, 개봉일이 같으면 ID 오름차순이다")
  void ordersByOpenAtThenId() {
    TimeCapsule later = mine("나중", OPEN.plusDays(1), CapsuleStatus.WRITING);
    TimeCapsule first = mine("먼저 1", OPEN, CapsuleStatus.WRITING);
    TimeCapsule second = mine("먼저 2", OPEN, CapsuleStatus.WRITING);

    assertThat(ids(findAll(null, null)))
        .containsExactly(first.getId(), second.getId(), later.getId());
  }

  @Test
  @DisplayName("개봉일이 같은 캡슐이 페이지 경계에 걸려도 빠지거나 겹치지 않는다")
  void cursorKeepsTiesAcrossPages() {
    TimeCapsule a = mine("A", OPEN, CapsuleStatus.WRITING);
    TimeCapsule b = mine("B", OPEN, CapsuleStatus.WRITING);
    TimeCapsule c = mine("C", OPEN, CapsuleStatus.WRITING);

    List<MyCapsule> first = page(null, null, Limit.of(2));
    TimeCapsule last = first.get(first.size() - 1).capsule();
    List<MyCapsule> second = page(last.getOpenAt(), last.getId(), Limit.of(2));

    assertThat(ids(first)).containsExactly(a.getId(), b.getId());
    assertThat(ids(second)).containsExactly(c.getId());
  }

  @Test
  @DisplayName("상태로 거르면 그 상태만 나온다")
  void filtersByStatus() {
    TimeCapsule opened = mine("열림", OPEN, CapsuleStatus.OPENED);
    mine("작성 중", OPEN, CapsuleStatus.WRITING);

    assertThat(ids(findAll(CapsuleStatus.OPENED, null))).containsExactly(opened.getId());
  }

  @Test
  @DisplayName("다른 멤버가 아니라 내 확인 시각을 같이 읽는다")
  void carriesMyOpenedAt() {
    TimeCapsule checked = mine("확인함", OPEN, CapsuleStatus.OPENED);
    TimeCapsule unchecked = mine("남만 확인함", OPEN, CapsuleStatus.OPENED);
    markOpened(memberOf(checked, me));
    markOpened(join(unchecked, friend, MemberStatus.ACTIVE));

    assertThat(findAll(null, null))
        .extracting(found -> found.capsule().getId(), MyCapsule::openedAt)
        .containsExactly(tuple(checked.getId(), OPEN), tuple(unchecked.getId(), null));
  }

  @Test
  @DisplayName("검색어가 이름 · 설명 · ACTIVE 멤버 닉네임 중 하나에 들어 있으면 나온다")
  void searchesTitleDescriptionAndNickname() {
    TimeCapsule byTitle = mine("제주 여행", OPEN, CapsuleStatus.WRITING);
    TimeCapsule byDescription = saveCapsule("캡슐", "제주에서의 추억", OPEN, CapsuleStatus.WRITING, null);
    join(byDescription, me, MemberStatus.ACTIVE);
    TimeCapsule byNickname = mine("캡슐", OPEN, CapsuleStatus.WRITING);
    join(byNickname, saveUser("list-jeju", "제주사람"), MemberStatus.ACTIVE);
    TimeCapsule removedNickname = mine("캡슐", OPEN, CapsuleStatus.WRITING);
    join(removedNickname, saveUser("list-jeju-removed", "제주떠난사람"), MemberStatus.REMOVED);

    assertThat(ids(findAll(null, "제주")))
        .containsExactly(byTitle.getId(), byDescription.getId(), byNickname.getId());
  }

  @Test
  @DisplayName("내 닉네임으로는 찾지 않는다")
  void doesNotMatchMyOwnNickname() {
    mine("캡슐", OPEN, CapsuleStatus.WRITING);
    TimeCapsule withFriend = mine("캡슐", OPEN, CapsuleStatus.WRITING);
    join(withFriend, saveUser("list-sang", "상민"), MemberStatus.ACTIVE);

    assertThat(ids(findAll(null, "상"))).containsExactly(withFriend.getId());
  }

  @Test
  @DisplayName("닉네임이 여러 명 맞아도 캡슐은 한 번만 나온다")
  void nicknameMatchesDoNotDuplicate() {
    TimeCapsule capsule = mine("캡슐", OPEN, CapsuleStatus.WRITING);
    join(capsule, saveUser("list-kim1", "김하나"), MemberStatus.ACTIVE);
    join(capsule, saveUser("list-kim2", "김둘"), MemberStatus.ACTIVE);

    assertThat(ids(findAll(null, "김"))).containsExactly(capsule.getId());
  }

  @Test
  @DisplayName("검색어의 % 와 _ 는 글자 그대로 찾는다")
  void escapesWildcards() {
    TimeCapsule percent = mine("100% 우정", OPEN, CapsuleStatus.WRITING);
    TimeCapsule underscore = mine("a_b", OPEN, CapsuleStatus.WRITING);
    mine("평범한 캡슐", OPEN, CapsuleStatus.WRITING);
    mine("axb", OPEN, CapsuleStatus.WRITING);

    assertThat(ids(findAll(null, "%"))).containsExactly(percent.getId());
    assertThat(ids(findAll(null, "_"))).containsExactly(underscore.getId());
  }

  @Test
  @DisplayName("상태별 개수는 검색어를 적용하고, 캡슐이 없는 상태는 나오지 않는다")
  void countsByStatusWithKeyword() {
    mine("제주 1", OPEN, CapsuleStatus.WRITING);
    mine("제주 2", OPEN, CapsuleStatus.WRITING);
    mine("제주 3", OPEN, CapsuleStatus.OPENED);
    mine("서울", OPEN, CapsuleStatus.LOCKED);

    assertThat(
            timeCapsuleRepository.countMyCapsulesByStatus(me.getId(), LikePattern.contains("제주")))
        .extracting(CapsuleStatusCount::status, CapsuleStatusCount::count)
        .containsExactlyInAnyOrder(
            tuple(CapsuleStatus.WRITING, 2L), tuple(CapsuleStatus.OPENED, 1L));
  }

  @Test
  @DisplayName("멤버 수는 ACTIVE 멤버만, 캡슐별로 한 번에 센다")
  void countsActiveMembersPerCapsule() {
    TimeCapsule two = mine("둘", OPEN, CapsuleStatus.WRITING);
    join(two, friend, MemberStatus.ACTIVE);
    join(two, saveUser("list-removed", "지수"), MemberStatus.REMOVED);
    TimeCapsule one = mine("하나", OPEN, CapsuleStatus.WRITING);

    assertThat(
            capsuleMemberRepository.countByTimeCapsuleIdInAndStatus(
                List.of(two.getId(), one.getId()), MemberStatus.ACTIVE))
        .extracting(CapsuleCount::capsuleId, CapsuleCount::count)
        .containsExactlyInAnyOrder(tuple(two.getId(), 2L), tuple(one.getId(), 1L));
  }

  @Test
  @DisplayName("편지 수는 제출되고 삭제되지 않은 편지만, 캡슐별로 한 번에 센다")
  void countsSubmittedLettersPerCapsule() {
    TimeCapsule capsule = mine("캡슐", OPEN, CapsuleStatus.WRITING);
    TimeCapsule empty = mine("빈 캡슐", OPEN, CapsuleStatus.WRITING);
    saveLetter(capsule, me, LetterStatus.SUBMITTED, null);
    saveLetter(capsule, friend, LetterStatus.SUBMITTED, null);
    saveLetter(capsule, saveUser("list-draft", "임시"), LetterStatus.DRAFT, null);
    saveLetter(capsule, saveUser("list-deleted", "삭제"), LetterStatus.SUBMITTED, OPEN);

    assertThat(
            timeCapsuleRepository.countLettersByStatusIn(
                List.of(capsule.getId(), empty.getId()), LetterStatus.SUBMITTED))
        .extracting(CapsuleCount::capsuleId, CapsuleCount::count)
        .containsExactly(tuple(capsule.getId(), 2L));
  }

  @Test
  @DisplayName("개봉일 범위 조회는 시작 시각을 넣고 끝 시각을 뺀다")
  void openAtBetweenIncludesFromExcludesTo() {
    LocalDateTime from = OPEN;
    LocalDateTime to = OPEN.plusDays(1);
    mine("전날 끝", from.minusMinutes(1), CapsuleStatus.WRITING);
    mine("시작", from, CapsuleStatus.WRITING);
    mine("끝 직전", to.minusMinutes(1), CapsuleStatus.WRITING);
    mine("끝", to, CapsuleStatus.WRITING);

    assertThat(timeCapsuleRepository.findMyOpenAtBetween(me.getId(), from, to))
        .containsExactlyInAnyOrder(from, to.minusMinutes(1));
  }

  @Test
  @DisplayName("개봉일 범위 조회도 내가 ACTIVE 멤버인 삭제되지 않은 캡슐만 본다")
  void openAtBetweenOnlyMyActiveCapsules() {
    mine("내 캡슐", OPEN, CapsuleStatus.WRITING);
    join(saveCapsule("남의 캡슐", OPEN, CapsuleStatus.WRITING), friend, MemberStatus.ACTIVE);
    join(saveCapsule("내보내진 캡슐", OPEN, CapsuleStatus.WRITING), me, MemberStatus.REMOVED);
    join(saveCapsule("삭제된 캡슐", OPEN, CapsuleStatus.WRITING, OPEN), me, MemberStatus.ACTIVE);

    assertThat(timeCapsuleRepository.findMyOpenAtBetween(me.getId(), OPEN, OPEN.plusDays(1)))
        .containsExactly(OPEN);
  }

  @Test
  @DisplayName("그 날 열리는 캡슐은 상태와 상관없이 개봉일 · ID 오름차순으로 나온다")
  void capsulesOpeningBetween() {
    TimeCapsule later = mine("오후", OPEN.plusHours(12), CapsuleStatus.LOCKED);
    TimeCapsule first = mine("자정 1", OPEN, CapsuleStatus.OPENED);
    TimeCapsule second = mine("자정 2", OPEN, CapsuleStatus.WRITING);
    mine("다음 날", OPEN.plusDays(1), CapsuleStatus.WRITING);
    join(saveCapsule("남의 캡슐", OPEN, CapsuleStatus.WRITING), friend, MemberStatus.ACTIVE);

    assertThat(
            ids(
                timeCapsuleRepository.findMyCapsulesOpeningBetween(
                    me.getId(), OPEN, OPEN.plusDays(1))))
        .containsExactly(first.getId(), second.getId(), later.getId());
  }

  private void markOpened(CapsuleMember member) {
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), OPEN));
  }

  private CapsuleMember memberOf(TimeCapsule capsule, User user) {
    return capsuleMemberRepository
        .findByTimeCapsuleIdAndUserIdAndStatus(capsule.getId(), user.getId(), MemberStatus.ACTIVE)
        .orElseThrow();
  }

  private List<MyCapsule> findAll(CapsuleStatus status, String keyword) {
    return timeCapsuleRepository.findMyCapsules(
        me.getId(), status, LikePattern.contains(keyword), null, null, ALL);
  }

  private List<MyCapsule> page(LocalDateTime cursorOpenAt, Long cursorId, Limit limit) {
    return timeCapsuleRepository.findMyCapsules(
        me.getId(), null, null, cursorOpenAt, cursorId, limit);
  }

  private List<Long> ids(List<MyCapsule> capsules) {
    return capsules.stream().map(found -> found.capsule().getId()).toList();
  }

  private TimeCapsule mine(String title, LocalDateTime openAt, CapsuleStatus status) {
    TimeCapsule capsule = saveCapsule(title, openAt, status);
    join(capsule, me, MemberStatus.ACTIVE);
    return capsule;
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private TimeCapsule saveCapsule(String title, LocalDateTime openAt, CapsuleStatus status) {
    return saveCapsule(title, openAt, status, null);
  }

  private TimeCapsule saveCapsule(
      String title, LocalDateTime openAt, CapsuleStatus status, LocalDateTime deletedAt) {
    return saveCapsule(title, null, openAt, status, deletedAt);
  }

  private TimeCapsule saveCapsule(
      String title,
      String description,
      LocalDateTime openAt,
      CapsuleStatus status,
      LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(me)
            .iconType("ICE_CREAM")
            .title(title)
            .description(description)
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(openAt.minusDays(10))
            .openAt(openAt)
            .deletedAt(deletedAt)
            .build());
  }

  private CapsuleMember join(TimeCapsule capsule, User user, MemberStatus status) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(user == me ? MemberRole.OWNER : MemberRole.PARTICIPANT)
            .status(status)
            .joinedAt(OPEN.minusDays(30))
            .build());
  }

  private void saveLetter(
      TimeCapsule capsule, User author, LetterStatus status, LocalDateTime deletedAt) {
    letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
            .author(author)
            .content("편지")
            .status(status)
            .deletedAt(deletedAt)
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}
