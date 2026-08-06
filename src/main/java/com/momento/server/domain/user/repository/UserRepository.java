package com.momento.server.domain.user.repository;

import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findBySocialProviderAndSocialId(OAuth2Provider socialProvider, String socialId);
}
