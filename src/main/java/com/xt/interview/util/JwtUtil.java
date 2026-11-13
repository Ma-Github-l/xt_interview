package com.xt.interview.util;

import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration:900000}")
    private long accessExpiration; // 15 分钟

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration; // 7 天

    /** 旧 refresh token 宽限期（毫秒）：刷新响应丢失时，旧 token 在 60s 内仍可再刷一次，避免用户被踢下线 */
    @Value("${jwt.refresh-grace-period:60000}")
    private long refreshGracePeriod;

    /**
     * 为 refresh token 附加轮换追踪 claims（JTI + IAT），
     * 供服务端判定"旧 token 被二次使用"或"宽限期内合法重试"。
     */
    public String generateRefreshTokenWithRotation(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "refresh");
        String jti = java.util.UUID.randomUUID().toString();
        claims.put("jti", jti);
        return buildToken(claims, new Date(System.currentTimeMillis() + refreshExpiration));
    }

    /** 读取 token 的 JTI（轮换追踪 ID），缺失返回 null */
    public String getTokenJti(String token) {
        try {
            return getClaims(token).getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 宽限期内旧 refresh token 是否仍可重试 */
    public boolean isRefreshTokenInGracePeriod(String token, long lastRotationTimeMillis) {
        Date iat;
        try {
            iat = getClaims(token).getIssuedAt();
        } catch (Exception e) {
            return false;
        }
        long now = System.currentTimeMillis();
        return iat.getTime() < lastRotationTimeMillis
                && (now - lastRotationTimeMillis) <= refreshGracePeriod;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 Access Token（短期，15 分钟） */
    public String generateAccessToken(Long userId, String username, Integer roleLevel) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("roleLevel", roleLevel);
        claims.put("type", "access");
        return buildToken(claims, new Date(System.currentTimeMillis() + accessExpiration));
    }

    private String buildToken(Map<String, Object> claims, Date expiration) {
        String subject = (String) claims.get("username");
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException | MalformedJwtException | SignatureException | UnsupportedJwtException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).get("username", String.class);
    }

    public Long getUserIdFromToken(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    public Integer getRoleLevelFromToken(String token) {
        return getClaims(token).get("roleLevel", Integer.class);
    }

    public String getTokenType(String token) {
        return getClaims(token).get("type", String.class);
    }

    /** 校验是否为有效的 Refresh Token */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = getClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (ExpiredJwtException | IllegalArgumentException e) {
            return false;
        }
    }

}
