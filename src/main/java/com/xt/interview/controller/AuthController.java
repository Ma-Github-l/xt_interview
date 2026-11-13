package com.xt.interview.controller;

import com.xt.interview.entity.Account;
import com.xt.interview.repository.AccountRepository;
import com.xt.interview.util.JwtUtil;
import com.xt.interview.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.AbstractMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Integer NOT_DELETED = 0;
    /** refresh cookie 有效期：与 refresh token 过期时间一致（7 天） */
    private static final int REFRESH_COOKIE_TTL_SECONDS = 7 * 24 * 3600;
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    /** 登录限流：每 IP 每分钟最多 10 次，防爆破 */
    private static final int LOGIN_MAX_ATTEMPTS_PER_MINUTE = 10;
    private final Map<String, Map.Entry<Long, AtomicInteger>> loginRateMap = new ConcurrentHashMap<>();

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Refresh Token 轮换状态：key = userId。
     * 记录"最近一次合法刷新"的时间和该次刷新时签发的旧 refresh token 的 JTI，
     * 用于判定旧 token 在宽限期内是否允许再刷，以及旧 token 是否被二次使用（疑似被盗）。
     */
    private final Map<Long, RotationRecord> rotationRecords = new ConcurrentHashMap<>();

    private static class RotationRecord {
        long lastRotationTime;
        String previousJti;
        RotationRecord(long t, String jti) {
            this.lastRotationTime = t;
            this.previousJti = jti;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        // 登录限流：同一 IP 每分钟最多 10 次
        String ip = httpReq.getRemoteAddr();
        long now = System.currentTimeMillis();
        Map.Entry<Long, AtomicInteger> entry = loginRateMap.get(ip);
        if (entry == null || now - entry.getKey() >= 60_000L) {
            loginRateMap.put(ip, new AbstractMap.SimpleEntry<>(now, new AtomicInteger(1)));
        } else {
            AtomicInteger count = entry.getValue();
            int c = count.incrementAndGet();
            if (c > LOGIN_MAX_ATTEMPTS_PER_MINUTE) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "请求过于频繁，请稍后再试");
                return ResponseEntity.status(429).body(resp);
            }
        }

        String username = request.get("username");
        String password = request.get("password");

        Map<String, Object> response = new HashMap<>();

        if (username == null || username.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "用户名不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        if (password == null || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Account account = accountRepository.findByUsernameAndDeleted(username.trim(), NOT_DELETED)
                    .orElse(null);

            if (account == null) {
                response.put("success", false);
                response.put("message", "用户名或密码错误");
                return ResponseEntity.badRequest().body(response);
            }

            // Argon2id 校验 + 存量 MD5 数据兼容回退（命中 MD5 时自动升级为 Argon2id 并回写）
            PasswordUtil.MatchResult mr = PasswordUtil.verify(password, account.getPassword());
            if (!mr.matched) {
                response.put("success", false);
                response.put("message", "用户名或密码错误");
                return ResponseEntity.badRequest().body(response);
            }
            if (mr.md5FallbackUsed) {
                account.setPassword(mr.upgradedHash);
                accountRepository.save(account);
            }

            if (account.getStatus() != 1) {
                response.put("success", false);
                response.put("message", "账号已禁用");
                return ResponseEntity.badRequest().body(response);
            }

            String accessToken = jwtUtil.generateAccessToken(account.getId(), account.getUsername(), account.getRoleLevel());
            String refreshToken = jwtUtil.generateRefreshTokenWithRotation(account.getId(), account.getUsername());

            // refresh token 通过 HttpOnly Cookie 下发（防 XSS），body 不再暴露
            setRefreshCookie(httpResp, refreshToken);
            // 登录成功后清除该用户的历史轮换状态，避免旧 token 误触发吊销逻辑
            rotationRecords.remove(account.getId());

            response.put("success", true);
            response.put("message", "登录成功");
            response.put("accessToken", accessToken);
            response.put("user", buildUserMap(account));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 刷新 token：用长期 refreshToken 换新的短期 accessToken + 新的 refreshToken
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody(required = false) Map<String, String> request,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        // 优先从 HttpOnly Cookie 读取；body 传参做兼容降级
        String refreshToken = getRefreshTokenFromCookie(httpReq);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = request != null ? request.get("refreshToken") : null;
        }

        Map<String, Object> response = new HashMap<>();

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "缺少 refreshToken");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // 1. 基本校验：类型 + 过期
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                response.put("success", false);
                response.put("message", "refreshToken 无效或已过期，请重新登录");
                return ResponseEntity.ok(response);
            }

            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            Account account = accountRepository.findByIdAndDeleted(userId, NOT_DELETED)
                    .orElse(null);

            if (account == null || account.getStatus() != 1) {
                response.put("success", false);
                response.put("message", "账号不可用，请重新登录");
                return ResponseEntity.ok(response);
            }

            // 2. Refresh Token 轮换：旧 token 只能使用一次
            //    宽限期内，旧 refresh token 被"再刷一次"是允许的（刷新响应丢失场景），
            //    但宽限期外再出现旧 token 说明被窃取复用，吊销该用户全部会话。
            String tokenJti = jwtUtil.getTokenJti(refreshToken);
            RotationRecord record = rotationRecords.get(userId);
            boolean isReplay = false;
            if (record != null) {
                if (record.previousJti != null && record.previousJti.equals(tokenJti)) {
                    // 宽限期内同一 token 重复刷新：仅放行一次（刷新响应丢失的兜底）
                    isReplay = !jwtUtil.isRefreshTokenInGracePeriod(refreshToken, record.lastRotationTime);
                } else if (record.previousJti != null) {
                    // 更旧的 token 再次出现：疑似被盗，吊销全部会话
                    isReplay = true;
                }
            }

            if (isReplay) {
                // 吊销该用户全部会话：清除轮换记录，要求重新登录
                rotationRecords.remove(userId);
                response.put("success", false);
                response.put("revoked", true);
                response.put("message", "检测到 refresh token 被复用，会话已吊销，请重新登录");
                return ResponseEntity.ok(response);
            }

            // 3. 轮换：签发新 refresh token（带 JTI），记录旧 JTI + 时间戳用于宽限期判定
            String newAccessToken = jwtUtil.generateAccessToken(account.getId(), account.getUsername(), account.getRoleLevel());
            String newRefreshToken = jwtUtil.generateRefreshTokenWithRotation(account.getId(), account.getUsername());
            rotationRecords.put(userId, new RotationRecord(System.currentTimeMillis(), tokenJti));

            // 新 refresh token 通过 HttpOnly Cookie 下发
            setRefreshCookie(httpResp, newRefreshToken);

            response.put("success", true);
            response.put("message", "刷新成功");
            response.put("accessToken", newAccessToken);
            response.put("user", buildUserMap(account));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse httpResp) {
        // 清除 refresh cookie（HttpOnly，前端无法手动删，必须服务端下发清除指令）
        clearRefreshCookie(httpResp);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "退出登录成功");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> response = new HashMap<>();

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.put("valid", false);
            response.put("message", "未提供token");
            return ResponseEntity.ok(response);
        }

        String token = authorization.substring(7);

        try {
            if (!("access".equals(jwtUtil.getTokenType(token)))) {
                response.put("valid", false);
                response.put("message", "token 无效或已过期");
                return ResponseEntity.ok(response);
            }

            String username = jwtUtil.getUsernameFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);
            Integer roleLevel = jwtUtil.getRoleLevelFromToken(token);

            response.put("valid", true);
            response.put("message", "token有效");
            response.put("user", Map.of(
                "id", userId,
                "username", username,
                "roleLevel", roleLevel
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("valid", false);
            response.put("message", "token无效: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 设置 refresh token 到 HttpOnly Cookie。
     * HttpOnly：JS 无法读取（防 XSS 窃取）；Secure：仅 HTTPS 传输（生产环境必配）。
     */
    private void setRefreshCookie(HttpServletResponse resp, String token) {
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE_NAME).append('=').append(token);
        sb.append("; Path=/");
        sb.append("; HttpOnly;");
        sb.append("SameSite=Lax;");
        sb.append("Max-Age=").append(REFRESH_COOKIE_TTL_SECONDS);
        // 生产环境强制 HTTPS 时加 Secure；本地 HTTP 开发环境不加强制（Nginx 反代层统一加 Secure）
        sb.append(" Secure;");
        resp.addHeader("Set-Cookie", sb.toString());
    }

    /** 从请求 Cookie 中读取 refresh token */
    private String getRefreshTokenFromCookie(HttpServletRequest req) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    /** 清除 refresh cookie（登出调用） */
    private void clearRefreshCookie(HttpServletResponse resp) {
        resp.addHeader("Set-Cookie",
                REFRESH_COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax; Secure");
    }

    /** 构造返回给前端的用户信息 map（login / refresh 共用） */
    private Map<String, Object> buildUserMap(Account account) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", account.getId());
        user.put("username", account.getUsername());
        user.put("name", account.getName());
        user.put("gender", account.getGender());
        user.put("phone", account.getPhone());
        user.put("roleLevel", account.getRoleLevel());
        user.put("status", account.getStatus());
        user.put("avatar", account.getAvatar());
        return user;
    }
}
