package io.github.senjar.bookingservice;

import io.github.senjar.bookingservice.config.KafkaTestcontainersConfiguration;
import io.github.senjar.bookingservice.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class BookingServiceApplicationTests {

    @Test
    @DisplayName("Should start the whole application context against real Postgres and Kafka")
    void contextLoads() {
    }
}
