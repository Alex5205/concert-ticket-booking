package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secretBase64,
            @Value("${jwt.expiration-ms}") long expirationMs,
            Clock clock
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseAndValidate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        Long userId = claims.get("userId", Long.class);

        if (username == null || userId == null) {
            throw new JwtException("В токене отсутствуют обязательные поля");
        }

        return new AuthenticatedUser(userId, username);
    }
}
