package io.github.senjar.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakRestClientConfig {

    @Bean
    public RestClient keycloakRestClient(RestClient.Builder builder,
                                         @Value("${keycloak.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
