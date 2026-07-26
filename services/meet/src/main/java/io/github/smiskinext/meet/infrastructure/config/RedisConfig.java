package io.github.smiskinext.meet.infrastructure.config;

import io.github.smiskinext.meet.infrastructure.persistence.model.JoinRequestData;
import io.github.smiskinext.meet.infrastructure.persistence.model.JoinRequestResultData;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis configuration for the join-request queue on Spring Data Redis 4.x / Jackson 3.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>{@link StringRedisTemplate} for the ZSET queue and STRING device index, and for executing
 *       the atomic Lua scripts.
 *   <li>a typed {@link RedisTemplate} that serializes {@link JoinRequestData} to JSON through a
 *       Jackson 3 {@link JsonMapper} and {@link JacksonJsonRedisSerializer}.
 * </ul>
 *
 * <p>The join-request {@link JsonMapper} is built privately and never exposed as a Spring bean: a
 * user-declared {@code JsonMapper} bean would make Boot back off its auto-configured web mapper
 * (which carries the {@code ProblemDetail} mixin that flattens RFC 9457 extension members), so
 * exposing it would corrupt {@code application/problem+json} responses across the service. The
 * mapper carries a {@link BasicPolymorphicTypeValidator} restricted to the meet domain package so
 * any future default-typing use stays confined to trusted types.
 *
 * <p>Only activates when {@code spring.data.redis.host} is set, matching the shared cache config.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {

    public static JsonMapper joinRequestJsonMapper() {
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType("io.github.smiskinext.meet.")
                .build();
        return JsonMapper.builder().polymorphicTypeValidator(validator).build();
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
    public RedisTemplate<String, JoinRequestData> joinRequestRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, JoinRequestData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        JacksonJsonRedisSerializer<JoinRequestData> valueSerializer =
                new JacksonJsonRedisSerializer<>(joinRequestJsonMapper(), JoinRequestData.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, JoinRequestResultData> joinRequestResultRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, JoinRequestResultData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        JacksonJsonRedisSerializer<JoinRequestResultData> valueSerializer =
                new JacksonJsonRedisSerializer<>(
                        joinRequestJsonMapper(), JoinRequestResultData.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
