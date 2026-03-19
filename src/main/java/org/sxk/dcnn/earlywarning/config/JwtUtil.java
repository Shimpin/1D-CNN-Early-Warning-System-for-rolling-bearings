package org.sxk.dcnn.earlywarning.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析
 */
public final class JwtUtil {

    public static String createToken(JwtProperties props, long userId, String username) {
        SecretKey key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        long expireMs = props.getExpireHours() * 3600L * 1000;
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expireMs))
            .signWith(key)
            .compact();
    }

    public static Claims parseToken(JwtProperties props, String token) {
        SecretKey key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public static Long getUserId(Claims claims) {
        Object o = claims.get("userId");
        if (o instanceof Number) return ((Number) o).longValue();
        return null;
    }
}
