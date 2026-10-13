package io.github.senjar.notificationservice.config;

import io.github.senjar.notificationservice.exception.EmailRejectedException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    private static final long MAX_INTERVAL_MS = 30_000;

    /**
     * Spring Boot attaches this handler to the listener container factory. A failed record is
     * retried with a growing delay, unless the failure is permanent. When the retries run out
     * the record is logged with everything needed to replay it, then skipped.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            @Value("${notification.kafka.retry.max-retries:5}") int maxRetries,
            @Value("${notification.kafka.retry.initial-interval:1s}") Duration initialInterval) {

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                KafkaConsumerConfig::logDiscardedRecord, backOff);
        handler.addNotRetryableExceptions(EmailRejectedException.class);
        return handler;
    }

    private static void logDiscardedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Giving up on record {}-{}@{} key={} value={}",
                record.topic(), record.partition(), record.offset(),
                record.key(), record.value(), exception);
    }
}
