package com.hhst.xsync.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hhst.xsync.entity.User;
import com.hhst.xsync.service.IUserService;
import com.hhst.xsync.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  @Autowired private IUserService userService;
  @Autowired private JwtUtils jwtUtils;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    String bearer = request.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      String token = bearer.substring(7);
      try {
        Claims claims = jwtUtils.parseToken(token);
        String email = jwtUtils.getSubject(claims);
        if (!jwtUtils.isExpired(claims)
            && email != null
            && userService.exists(new QueryWrapper<>(User.class).eq("email", email))) {
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(
                  email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      }
    }
    filterChain.doFilter(request, response);
  }
}
