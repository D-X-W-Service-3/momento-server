package com.momento.server.domain.user.repository;

import com.momento.server.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /** 탈퇴 회원도 포함해 조회한다. 같은 카카오 계정의 재가입 여부를 판단해야 하기 때문이다. */
  Optional<User> findByKakaoId(String kakaoId);

  Optional<User> findByIdAndDeletedAtIsNull(Long id);

  /** 탈퇴 회원의 닉네임도 센다. 탈퇴는 소프트 삭제라 행이 남아 닉네임을 계속 차지한다. */
  boolean existsByNickname(String nickname);

  /** 이 닉네임을 나 말고 다른 회원이 쓰고 있는지. 지금 내 닉네임으로 확인하거나 저장하면 false 다. */
  boolean existsByNicknameAndIdNot(String nickname, Long id);
}
