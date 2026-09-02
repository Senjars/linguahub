package io.github.senjar.courseservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @GetMapping("/api/payments/subscriptions/{studentId}/status")
    SubscriptionStatusDto getSubscriptionStatus(@PathVariable Long studentId);
}
