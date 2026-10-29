package com.momento.server.domain.user.service;

import com.momento.server.domain.user.dto.request.NotificationSettingsUpdateRequest;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.exception.UserErrorCode;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.exception.ApiException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private static final String TEMPORARY_NICKNAME_PREFIX = "모멘토친구";
  private static final int TEMPORARY_NICKNAME_ATTEMPTS = 10;

  private final UserRepository userRepository;

  public Optional<User> findByKakaoId(String kakaoId) {
    return userRepository.findByKakaoId(kakaoId);
  }

  /** 탈퇴하지 않은 회원을 조회한다. 인증 필터를 통과한 요청이라도 다른 도메인이 회원 엔티티를 쓸 때는 이걸로 가져온다. */
  public User getActiveUser(Long userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
  }

  /**
   * 보낸 필드만 바꾼다. 이메일 · 전화번호는 빈 문자열이면 지운다.
   *
   * <p>닉네임은 저장 전에 다른 회원이 쓰는지 먼저 확인해 409 로 돌려준다. 다만 두 요청이 동시에 같은 닉네임으로 바꾸면 둘 다 확인을 통과하므로, 최종 판정은 DB
   * 의 닉네임 UNIQUE 다. {@code saveAndFlush} 로 위반을 이 자리에서 드러내 같은 409 로 바꾼다. 커밋 시점까지 미루면 트랜잭션 밖에서 터져 500
   * 이 된다. 이 테이블에서 수정으로 걸릴 수 있는 UNIQUE 는 닉네임뿐이다.
   */
  @Transactional
  public User updateProfile(Long userId, UserUpdateRequest request) {
    User user = getActiveUser(userId);

    if (request.nickname() != null) {
      if (userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
        throw new ApiException(UserErrorCode.NICKNAME_DUPLICATED);
      }
      user.updateNickname(request.nickname());
    }
    if (request.birthday() != null) {
      user.updateBirthday(request.birthday());
    }
    if (request.email() != null) {
      user.updateEmail(emptyToNull(request.email()));
    }
    if (request.phoneNumber() != null) {
      user.updatePhoneNumber(emptyToNull(request.phoneNumber()));
    }

    try {
      return userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      throw new ApiException(UserErrorCode.NICKNAME_DUPLICATED, exception);
    }
  }

  /** 보낸 항목만 바꾼다. 알림 설정에는 UNIQUE 같은 제약이 없어 dirty checking 으로 저장한다. */
  @Transactional
  public void updateNotificationSettings(Long userId, NotificationSettingsUpdateRequest request) {
    getActiveUser(userId)
        .getNotificationSettings()
        .update(
            request.capsuleOpened(),
            request.capsuleDDay(),
            request.letterDeadline(),
            request.newMember(),
            request.memoryReminder());
  }

  /** 다른 회원이 쓰지 않는 닉네임인지. 지금 내 닉네임이면 사용할 수 있다. */
  public boolean isNicknameAvailable(Long userId, String nickname) {
    return !userRepository.existsByNicknameAndIdNot(nickname, userId);
  }

  private String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }

  /**
   * 새 회원은 다른 회원과 겹치지 않는 임시 닉네임으로 가입한다. 사용자는 가입 직후 프로필 설정에서 닉네임을 정한다.
   *
   * <p>{@code saveAndFlush} 로 UNIQUE 위반(임시 닉네임 · 카카오 ID)을 이 자리에서 {@code
   * DataIntegrityViolationException} 으로 드러낸다. 다시 시도할지는 호출하는 쪽이 정한다.
   */
  @Transactional
  public User register(String kakaoId, String profileImageUrl) {
    return userRepository.saveAndFlush(
        User.builder()
            .kakaoId(kakaoId)
            .nickname(newTemporaryNickname())
            .profileImageUrl(profileImageUrl)
            .build());
  }

  /**
   * {@code 모멘토친구} 뒤에 숫자 6자리를 붙이고, 이미 있으면 다시 뽑는다. 경우의 수가 90만 개라 겹칠 일이 드물다. 동시에 가입한 두 사람이 같은 번호를 뽑으면
   * 닉네임 UNIQUE 에 걸리고, 로그인 쪽에서 한 번 더 가입시킨다.
   */
  private String newTemporaryNickname() {
    for (int attempt = 0; attempt < TEMPORARY_NICKNAME_ATTEMPTS; attempt++) {
      String candidate =
          TEMPORARY_NICKNAME_PREFIX + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("겹치지 않는 임시 닉네임을 만들지 못했습니다.");
  }

  /** 탈퇴했던 회원이면 되살린다. 이미 활성 회원이면 아무것도 하지 않는다. */
  @Transactional
  public User restoreIfWithdrawn(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

    if (user.isWithdrawn()) {
      user.restore();
    }

    return user;
  }
}
