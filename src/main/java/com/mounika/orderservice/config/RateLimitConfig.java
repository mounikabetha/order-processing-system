package com.mounika.orderservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitInterceptor rateLimitInterceptor(StringRedisTemplate redisTemplate) {
        return new RateLimitInterceptor(redisTemplate);
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class RateLimitInterceptor implements HandlerInterceptor {

        private static final int MAX_REQUESTS = 50;
        private static final Duration WINDOW = Duration.ofMinutes(1);

        private final StringRedisTemplate redisTemplate;

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) throws Exception {

            String clientIp = getClientIp(request);
            String key = "rate_limit:" + clientIp;

            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(key, WINDOW.toSeconds(), TimeUnit.SECONDS);
            }

            // Add rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
            long remaining = Math.max(0, MAX_REQUESTS - (currentCount != null ? currentCount : 0));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            response.setHeader("X-RateLimit-Reset", String.valueOf(ttl != null ? ttl : WINDOW.toSeconds()));

            if (currentCount != null && currentCount > MAX_REQUESTS) {
                log.warn("Rate limit exceeded for client: ip={}, count={}", clientIp, currentCount);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                        {
                            "type": "about:blank",
                            "title": "Too Many Requests",
                            "status": 429,
                            "detail": "Rate limit exceeded. Max %d requests per minute."
                        }
                        """.formatted(MAX_REQUESTS));
                return false;
            }

            return true;
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }
}
