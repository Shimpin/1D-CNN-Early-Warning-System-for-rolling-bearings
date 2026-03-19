package org.sxk.dcnn.earlywarning.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * JWT 鉴权拦截器：从 Header 或 Cookie 取 Token，校验后写入 request 属性供后续使用。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER_TOKEN = "Authorization";
    private static final String PREFIX_BEARER = "Bearer ";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_USERNAME = "username";
    private static final String REDIS_TOKEN_PREFIX = "token:";

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    public JwtInterceptor(JwtProperties jwtProperties, StringRedisTemplate redisTemplate) {
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = getToken(request);
        if (token == null || token.isEmpty()) {
            sendRedirectToLogin(request, response);
            return false;
        }
        try {
            Claims claims = JwtUtil.parseToken(jwtProperties, token);
            String username = claims.getSubject();
            Long userId = JwtUtil.getUserId(claims);
            String redisKey = REDIS_TOKEN_PREFIX + token;
            try {
                if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
                    sendRedirectToLogin(request, response);
                    return false;
                }
            } catch (Exception ignored) {
                // Redis 不可用时仅校验 JWT 有效性
            }
            request.setAttribute(ATTR_USER_ID, userId);
            request.setAttribute(ATTR_USERNAME, username);
            return true;
        } catch (Exception e) {
            sendRedirectToLogin(request, response);
            return false;
        }
    }

    private String getToken(HttpServletRequest request) {
        String auth = request.getHeader(HEADER_TOKEN);
        if (auth != null && auth.startsWith(PREFIX_BEARER)) {
            return auth.substring(PREFIX_BEARER.length()).trim();
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("token".equals(c.getName())) return c.getValue();
            }
        }
        return null;
    }

    private void sendRedirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        boolean isApi = uri != null && uri.startsWith("/api/");
        if (isApi) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
    }

    /** 将 Token 存入 Redis，供拦截器校验（2 小时） */
    public void saveTokenToRedis(String token) {
        try {
            String key = REDIS_TOKEN_PREFIX + token;
            redisTemplate.opsForValue().set(key,"1", jwtProperties.getExpireHours(), TimeUnit.HOURS);
        } catch (Exception ignored) { }
    }

    /** 退出时移除 Redis 中的 Token */
    public void removeTokenFromRedis(String token) {
        try {
            if (token != null && !token.isEmpty()) {
                redisTemplate.delete(REDIS_TOKEN_PREFIX+ token);
            }
        } catch (Exception ignored) { }
    }
}
