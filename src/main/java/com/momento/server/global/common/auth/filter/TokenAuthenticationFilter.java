package com.momento.server.global.common.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.global.common.auth.service.TokenProvider;
import com.momento.server.global.common.code.ErrorCode;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String BEARER_AUTH = "Bearer ";

  private final TokenProvider tokenProvider;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authorizationHeader = request.getHeader(HEADER_AUTHORIZATION);
    String accessToken = getAccessToken(authorizationHeader);

    if (response.isCommitted()) {
      return;
    }

    if (!tokenProvider.validToken(accessToken)) {
      sendUnauthorizedResponse(response);
      return;
    }

    Authentication authentication = tokenProvider.getAuthentication(accessToken);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }

  private String getAccessToken(String authorizationHeader) {
    if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_AUTH)) {
      return authorizationHeader.substring(BEARER_AUTH.length());
    }
    return null;
  }

  private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
    ErrorCode errorCode = GlobalErrorCode.INVALID_ACCESS_TOKEN;

    response.addHeader("Content-Type", "application/json; charset=UTF-8");
    response.setStatus(errorCode.getStatus().value());
    response.getWriter().write(objectMapper.writeValueAsString(CommonResponse.error(errorCode)));
    response.getWriter().flush();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    String[] excludes = {
      "/health-check", "/oauth2/", "/login/", "/swagger-ui/", "/v3/api-docs", "/api-docs/"
    };
    String path = request.getRequestURI();

    return Arrays.stream(excludes).anyMatch(path::startsWith);
  }
}
