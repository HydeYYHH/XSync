package com.hhst.xsync.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtUtils {

  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.expiration-ms}")
  private long expirationMs;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(String subject) {
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + expirationMs)) // an hour
        .signWith(getSigningKey())
        .compact();
  }

  public Claims parseToken(String token) throws JwtException {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  public boolean isExpired(Claims claims) {
    return claims.getExpiration().before(new Date());
  }

  public String getSubject(Claims claims) {
    return claims.getSubject();
  }

  public Optional<String> extractUserSubject(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer == null || !bearer.startsWith("Bearer ")) {
      return Optional.empty();
    }
    String token = bearer.substring(7);
    Claims claims = parseToken(token);
    return Optional.of(claims.getSubject());
  }

}
