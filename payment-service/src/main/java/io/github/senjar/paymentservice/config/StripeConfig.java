package io.github.senjar.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {
    private String secretKey;
    private String webhookSecret;
    private String appUrl;
}
