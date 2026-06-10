package com.courseqa.util;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
@Component
public class JwtUtil {
@Value("${app.jwt.secret}")
private String secret;

@Value("${app.jwt.expiration-ms}")
private long expirationMs;

private Key getSigningKey() {
   
    return Keys.hmacShaKeyFor(secret.getBytes());
}

public String generateToken(String email){
    return Jwts.builder()
    .setSubject(email)
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
    .signWith(getSigningKey(),SignatureAlgorithm.HS256)
    .compact();
}

private Claims extractClaims(String token){
    return Jwts.parserBuilder()
    .setSigningKey(getSigningKey())
    .build().parseClaimsJws(token)
    .getBody();
}

 public String extractEmail(String token){
    return extractClaims(token).getSubject();
 }

public boolean isTokenValid(String token){
    try {
      return !extractClaims(token).getExpiration().before(new Date());
    }catch (Exception e) {
        return false;
    }

}
}
