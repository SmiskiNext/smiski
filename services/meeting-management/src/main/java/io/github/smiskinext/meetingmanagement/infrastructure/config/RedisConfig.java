package io.github.smiskinext.meetingmanagement.infrastructure.config;

import io.github.smiskinext.meetingmanagement.infrastructure.persistence.model.JoinRequestData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for join request queue storage.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link StringRedisTemplate} for queue (ZSET) and device index (STRING) operations
 *   <li>{@link RedisTemplate} with JSON serialization for join request metadata storage
 * </ul>
 *
 * <p>Only activates when {@code spring.data.redis.host} is set.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {

    /**
     * Template for string-based Redis operations (queue ZSET + device index STRING).
     *
     * <p>Uses {@link StringRedisSerializer} for both keys and values to ensure compatibility with
     * Redis CLI inspection and cross-language interop.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * Template for JSON-serialized join request metadata storage.
     *
     * <p>Uses Jackson to serialize {@link JoinRequestData} records to JSON strings.
     * Replaces the previous HASH-based storage with JSON STRING for better performance
     * and type safety.
     */
    @Bean
    public RedisTemplate<String, JoinRequestData> joinRequestRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, JoinRequestData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        JacksonJsonRedisSerializer<JoinRequestData> jsonSerializer =
                new JacksonJsonRedisSerializer<>(JoinRequestData.class);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
