package io.github.senjar.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String apiKey;

    public StripeConfig() {
        this.apiKey = System.getenv("STRIPE_API_KEY");
    }
}
