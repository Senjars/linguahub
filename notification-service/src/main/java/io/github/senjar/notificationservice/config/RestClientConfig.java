package io.github.senjar.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient resendRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }
}
