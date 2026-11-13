package com.xt.interview.filter;

import com.xt.interview.util.JwtUtil;
import com.xt.interview.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JWT 鉴权过滤器（OncePerRequestFilter，每请求执行一次）。
 * 职责：
 *   1. 校验 Authorization 头中的 Bearer access token（短期 15min）
 *   2. 将 userId / username / roleLevel / isSuper 写入 request attribute，
 *      供 Controller 层 AuthContext 读取，实现接口级权限控制
 *   3. 公开路径跳过鉴权：/api/auth/**（登录/刷新/登出）和 GET /api/avatars/*
 *
 * 安全设计：
 *   - 只接受 type=access 的 token（refresh token 不能直接调业务接口）
 *   - isSuper 带 5 分钟 TTL 缓存（避免每请求查 DB，权限变更最多延迟 5min 生效）
 *   - refresh token 通过 HttpOnly Cookie 下发，JS 无法读取（防 XSS 窃取）
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AccountRepository accountRepository;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** isSuper 缓存：key=userId，value=缓存条目（TTL 5 分钟） */
    private record IsSuperCache(int isSuper, AtomicLong expiresAtMillis) {}
    private final ConcurrentHashMap<Long, IsSuperCache> isSuperCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private int resolveIsSuper(Long userId) {
        IsSuperCache cached = isSuperCache.get(userId);
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMillis().get()) {
            return cached.isSuper();
        }
        int val = 0;
        try {
            val = accountRepository.findByIdAndDeleted(userId, 0)
                    .map(a -> a.getIsSuper() == null ? 0 : a.getIsSuper())
                    .orElse(0);
        } catch (Exception ignored) {
            // 数据库未就绪时按普通用户处理
        }
        isSuperCache.put(userId, new IsSuperCache(val, new AtomicLong(System.currentTimeMillis() + CACHE_TTL_MS)));
        return val;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // 登录 / 刷新 / 注销等认证接口不需要 token
        if (pathMatcher.match("/api/auth/**", path)) {
            return true;
        }
        // 只有 GET 读取头像文件是公开的（img 标签不便携带 Authorization 头）；
        // 其余请求（如 POST /api/avatars 上传）必须走鉴权，否则 @RequestAttribute("userId") 无法解析，导致 400
        if (pathMatcher.match("/api/avatars/*", path)
                && "GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            sendErrorResponse(response, 401, "未授权访问，请先登录", null);
            return;
        }

        String token = authorization.substring(7);

        try {
            boolean valid = jwtUtil.isTokenValid(token);
            if (!valid) {
                sendErrorResponse(response, 401, "token已过期，请重新登录", "expired");
                return;
            }

            // 只允许 access token 访问业务接口；refresh token 不能直接调业务
            if (!"access".equals(jwtUtil.getTokenType(token))) {
                sendErrorResponse(response, 401, "请使用 Access Token 访问", "wrong-type");
                return;
            }

            String username = jwtUtil.getUsernameFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);
            Integer roleLevel = jwtUtil.getRoleLevelFromToken(token);

            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("roleLevel", roleLevel);
            // isSuper 带 5 分钟 TTL 缓存（改权限后最多 5 分钟生效）
            request.setAttribute("isSuper", resolveIsSuper(userId));

        } catch (Exception e) {
            sendErrorResponse(response, 401, "token无效，请重新登录", "invalid");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message, String reason) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (reason != null) {
            response.setHeader("X-Auth-Reason", reason);
        }

        String escapedMessage = escapeJson(message);
        String jsonResponse = String.format(
            "{\"success\":false,\"message\":\"%s\",\"code\":%d}",
            escapedMessage, status
        );
        response.getWriter().write(jsonResponse);
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
