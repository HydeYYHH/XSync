package com.hhst.xsync.config;

import com.hhst.xsync.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@Component
public class JwtAuthenticationFilter extends GenericFilterBean {

  @Autowired private UserDetailsService userDetailsService;
  @Autowired private JwtUtils jwtUtils;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String bearer = ((HttpServletRequest) request).getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      String token = bearer.substring(7);
      try {
        Claims claims = jwtUtils.parseToken(token);
        String email = jwtUtils.getSubject(claims);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception e) {
        logger.error(e.getMessage());
        SecurityContextHolder.clearContext();
        ((HttpServletResponse) response)
            .sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
