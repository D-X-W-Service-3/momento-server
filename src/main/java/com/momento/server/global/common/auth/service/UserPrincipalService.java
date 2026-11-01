package com.momento.server.global.common.auth.service;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.exception.UserErrorCode;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** JWT 의 subject(회원 ID)로 principal 을 만든다. 탈퇴한 회원의 토큰은 인증되지 않는다. */
@Service
@RequiredArgsConstructor
public class UserPrincipalService implements UserDetailsService {

  private final UserRepository userRepository;

  public UserPrincipal loadByUserId(Long userId) {
    User user =
        userRepository
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

    return UserPrincipal.fromEntity(user);
  }

  @Override
  public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
    return loadByUserId(Long.valueOf(userId));
  }
}
