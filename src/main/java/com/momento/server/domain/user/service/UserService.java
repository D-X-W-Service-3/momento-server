package com.momento.server.domain.user.service;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.exception.UserErrorCode;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.exception.ApiException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

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

  @Transactional
  public User register(String kakaoId, String nickname, String profileImageUrl) {
    return userRepository.save(
        User.builder()
            .kakaoId(kakaoId)
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .build());
  }

  /** 탈퇴했던 회원이면 카카오 프로필로 되살린다. 이미 활성 회원이면 아무것도 하지 않는다. */
  @Transactional
  public User restoreIfWithdrawn(Long userId, String nickname, String profileImageUrl) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

    if (user.isWithdrawn()) {
      user.restore(nickname, profileImageUrl);
    }

    return user;
  }
}
