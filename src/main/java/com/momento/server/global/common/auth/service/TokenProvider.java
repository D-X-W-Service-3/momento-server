package com.momento.server.global.common.auth.service;

import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.property.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.xml.bind.DatatypeConverter;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** 액세스 토큰 발급/검증. subject 에 회원 ID 를 담는다. */
@Service
@RequiredArgsConstructor
public class TokenProvider {

  private final JwtProperties jwtProperties;
  private final UserPrincipalService userPrincipalService;
  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

  private Key createSecretKey() {
    byte[] apiKeySecretBytes = DatatypeConverter.parseBase64Binary(jwtProperties.getSecretKey());
    return new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());
  }

  public String generateToken(User user, Duration expiredAt) {
    Date now = new Date();
    return makeToken(new Date(now.getTime() + expiredAt.toMillis()), user);
  }

  private String makeToken(Date expiry, User user) {
    Date now = new Date();

    return Jwts.builder()
        .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
        .setIssuer(jwtProperties.getIssuer())
        .setIssuedAt(now)
        .setExpiration(expiry)
        .setSubject(String.valueOf(user.getId()))
        .signWith(createSecretKey(), signatureAlgorithm)
        .compact();
  }

  /** 서명/만료뿐 아니라 subject 가 회원 ID 형식인지까지 확인한다. */
  public boolean validToken(String token) {
    if (token == null) {
      return false;
    }
    try {
      Long.parseLong(getClaims(token).getSubject());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public Authentication getAuthentication(String token) {
    UserPrincipal principal = userPrincipalService.loadByUserId(getUserId(token));

    return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
  }

  public Long getUserId(String token) {
    return Long.valueOf(getClaims(token).getSubject());
  }

  private Claims getClaims(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(createSecretKey())
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}
