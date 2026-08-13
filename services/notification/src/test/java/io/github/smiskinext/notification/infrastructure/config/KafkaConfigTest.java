package io.github.smiskinext.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.kafka.CloudEventDeserializer;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

class KafkaConfigTest {

    private final KafkaConfig kafkaConfig = new KafkaConfig();

    private final KafkaProperties kafkaProperties = new KafkaProperties();

    @Test
    void consumerFactoriesUseDistinctRandomGroupIds() {
        String firstGroupId = groupId(kafkaConfig.joinCreatedConsumerFactory(kafkaProperties));
        String secondGroupId = groupId(kafkaConfig.joinResolvedConsumerFactory(kafkaProperties));
        String thirdGroupId = groupId(kafkaConfig.joinCreatedConsumerFactory(kafkaProperties));
        String fourthGroupId = groupId(kafkaConfig.joinResolvedConsumerFactory(kafkaProperties));

        assertThat(firstGroupId).startsWith("notification-join-created-sse-");
        assertThat(secondGroupId).startsWith("notification-join-resolved-sse-");
        assertThat(thirdGroupId).isNotEqualTo(firstGroupId);
        assertThat(fourthGroupId).isNotEqualTo(secondGroupId);
    }

    @Test
    void consumerFactoriesDecodeCloudEventsFromLatestOffset() {
        Map<String, Object> props = kafkaConfig
                .joinCreatedConsumerFactory(kafkaProperties)
                .getConfigurationProperties();

        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(CloudEventDeserializer.class);
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("latest");
    }

    private static String groupId(
            org.springframework.kafka.core.ConsumerFactory<String, io.cloudevents.CloudEvent>
                    factory) {
        return (String) factory.getConfigurationProperties().get(ConsumerConfig.GROUP_ID_CONFIG);
    }
}
