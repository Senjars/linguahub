package io.github.senjar.paymentservice;

import io.github.senjar.paymentservice.config.KafkaTestcontainersConfiguration;
import io.github.senjar.paymentservice.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
