package io.github.senjar.notificationservice;

import io.github.senjar.notificationservice.config.KafkaTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(KafkaTestcontainersConfiguration.class)
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}