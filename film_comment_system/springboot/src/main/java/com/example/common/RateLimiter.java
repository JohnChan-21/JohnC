package com.example.common;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的固定窗口限流器（简单计数）。
 * 以 key 为维度在指定窗口内累计次数：首次计数时设置过期时间，超过阈值即拒绝。
 * Redis 不可用时放行（fail-open），仅记录告警——限流是防滥用兜底，不应成为业务单点故障。
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取一次许可。
     *
     * @param key           限流维度，如 login:ip:1.2.3.4、register:ip:1.2.3.4、upload:limit:USER:5
     * @param limit         窗口内允许的最大次数
     * @param windowSeconds 窗口长度（秒）
     * @return true 允许本次操作；false 表示超过阈值
     */
    public boolean tryAcquire(String key, long limit, long windowSeconds) {
        try {
            // 使用 StringRedisTemplate：INCR 需要纯整数计数，JSON 序列化会导致计数失效
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("限流校验失败，已放行：key={}", key, e);
            return true;
        }
    }
}
