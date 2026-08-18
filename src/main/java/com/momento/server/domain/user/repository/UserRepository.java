package com.momento.server.domain.user.repository;

import com.momento.server.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /** 탈퇴 회원도 포함해 조회한다. 같은 카카오 계정의 재가입 여부를 판단해야 하기 때문이다. */
  Optional<User> findByKakaoId(String kakaoId);

  Optional<User> findByIdAndDeletedAtIsNull(Long id);
}
