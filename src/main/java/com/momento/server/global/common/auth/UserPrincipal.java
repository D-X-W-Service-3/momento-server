package com.momento.server.global.common.auth;

import com.momento.server.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 인증된 요청의 principal. 컨트롤러에서 {@code @AuthenticationPrincipal} 로 받아 {@link #getUserId()} 를 사용한다. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPrincipal implements UserDetails {

  @Getter private final Long userId;

  public static UserPrincipal fromEntity(User user) {
    return new UserPrincipal(user.getId());
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return String.valueOf(userId);
  }
}
