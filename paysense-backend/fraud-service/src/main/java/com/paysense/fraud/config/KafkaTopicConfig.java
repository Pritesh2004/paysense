package com.paysense.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic auto-creation for fraud service.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic fraudAlertTopic() {
        return TopicBuilder.name("fraud.alert")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
