package io.github.senjar.userservice;

import io.github.senjar.userservice.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
