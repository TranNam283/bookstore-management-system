package com.bookstore.backend.utils;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Lấy thông tin người dùng đang đăng nhập từ header {@code Authorization: Bearer <token>}. */
@Component
public class CurrentUserResolver {

  private final JwtUtils jwtUtils;

  public CurrentUserResolver(JwtUtils jwtUtils) {
    this.jwtUtils = jwtUtils;
  }

  public Optional<Map<String, Object>> resolve(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return Optional.empty();
    }
    try {
      return Optional.of(jwtUtils.getUserFromToken(authHeader.substring(7)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /** Id người dùng trong token, hoặc null nếu không có / token không hợp lệ. */
  public String resolveUserId(String authHeader) {
    return resolve(authHeader).map(user -> (String) user.get("id")).orElse(null);
  }
}
