package com.momento.server.domain.user.service;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.exception.UserErrorCode;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.exception.ApiException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
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

  /** 새 회원은 다른 회원과 겹치지 않는 임시 닉네임으로 가입한다. 사용자는 가입 직후 프로필 설정에서 닉네임을 정한다. */
  @Transactional
  public User register(String kakaoId, String profileImageUrl) {
    return userRepository.save(
        User.builder()
            .kakaoId(kakaoId)
            .nickname(newTemporaryNickname())
            .profileImageUrl(profileImageUrl)
            .build());
  }

  /**
   * {@code 모멘토친구} 뒤에 숫자 6자리를 붙이고, 이미 있으면 다시 뽑는다. 경우의 수가 90만 개라 겹칠 일이 드물다. 동시에 가입한 두 사람이 같은 번호를 뽑으면
   * 닉네임 UNIQUE 에 걸려 한 사람의 가입이 실패하고, 다시 로그인하면 된다.
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
