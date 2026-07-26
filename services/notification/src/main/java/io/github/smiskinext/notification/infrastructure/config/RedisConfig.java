package io.github.smiskinext.notification.infrastructure.config;

import io.github.smiskinext.notification.infrastructure.persistence.model.JoinDecisionData;
import io.github.smiskinext.notification.infrastructure.persistence.model.PendingJoinRequestData;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Redis configuration for the notification pending-join-request store on Spring Data Redis 4.x /
 * Jackson 3.
 *
 * <p>Provides a {@link StringRedisTemplate} for the per-meeting ZSET index and a typed
 * {@link RedisTemplate} that serializes {@link PendingJoinRequestData} through a Jackson 3
 * {@link JsonMapper}. Only activates when {@code spring.data.redis.host} is set.
 *
 * <p>The {@link JsonMapper} is built privately and never exposed as a Spring bean: a user-declared
 * {@code JsonMapper} bean would make Boot back off its auto-configured web mapper (which carries the
 * {@code ProblemDetail} mixin that flattens RFC 9457 extension members), corrupting {@code
 * application/problem+json} responses.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {

    private static JsonMapper pendingJoinRequestJsonMapper() {
        return JsonMapper.builder().build();
    }

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

    @Bean
    public RedisTemplate<String, PendingJoinRequestData> pendingJoinRequestRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, PendingJoinRequestData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        JacksonJsonRedisSerializer<PendingJoinRequestData> valueSerializer =
                new JacksonJsonRedisSerializer<>(
                        pendingJoinRequestJsonMapper(), PendingJoinRequestData.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, JoinDecisionData> joinDecisionRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, JoinDecisionData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        JacksonJsonRedisSerializer<JoinDecisionData> valueSerializer =
                new JacksonJsonRedisSerializer<>(
                        pendingJoinRequestJsonMapper(), JoinDecisionData.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
