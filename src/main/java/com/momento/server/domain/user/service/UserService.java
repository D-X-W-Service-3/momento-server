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
