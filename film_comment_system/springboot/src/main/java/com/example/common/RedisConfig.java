package com.example.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 自定义序列化方式：Key 使用 String，Value 使用 JSON
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        // Value 使用 JSON 序列化（带类型白名单，见 buildSafeJsonSerializer）
        GenericJackson2JsonRedisSerializer jsonSerializer = buildSafeJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        return template;
    }

    /**
     * 构造带类型白名单的 JSON 反序列化器。
     * GenericJackson2JsonRedisSerializer 默认会开启「类型信息」，反序列化时按 JSON 中的 @class
     * 字段实例化任意类型；一旦 Redis 被写入恶意 JSON（如未授权访问、被注入），即可构成反序列化 RCE 面。
     * 这里显式收窄 PolymorphicTypeValidator，只允许缓存实际用到的类型参与多态反序列化。
     */
    private GenericJackson2JsonRedisSerializer buildSafeJsonSerializer() {
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.entity.")   // 业务实体（Film 等）
                .allowIfSubType("cn.dev33.satoken.")     // Sa-Token 会话类型（防御性：避免与框架共用序列化时误伤登录）
                .allowIfSubType("java.util.")            // ArrayList / HashMap 等集合
                .allowIfSubType("java.lang.")            // String / Integer / Double 等
                .allowIfSubType("java.math.")            // BigDecimal 等
                .allowIfSubType("[")                     // 数组（评分缓存用 Object[]）
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}