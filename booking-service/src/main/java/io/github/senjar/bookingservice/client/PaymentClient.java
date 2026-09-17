package io.github.senjar.bookingservice.client;

import io.github.senjar.bookingservice.dto.payment.PaymentResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/single-lesson")
    PaymentResponseDto createLessonPayment(
            @RequestParam("userId") Long userId,
            @RequestParam("bookingId") Long bookingId);
}