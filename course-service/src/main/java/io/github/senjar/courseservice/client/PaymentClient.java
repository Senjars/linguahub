package io.github.senjar.courseservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @GetMapping("/api/v1/payments/test")
    String testPaymentService();
}
