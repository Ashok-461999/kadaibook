package com.kadaibook.security;

import com.kadaibook.config.JwtProperties;
import com.kadaibook.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "kadai-book";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.ttl() == null ? Duration.ofHours(12) : properties.ttl();
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim("phone", user.getPhone())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Optional<UUID> subject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!ISSUER.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
