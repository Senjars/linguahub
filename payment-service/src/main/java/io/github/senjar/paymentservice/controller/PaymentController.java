package io.github.senjar.paymentservice.controller;

import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import io.github.senjar.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public Page<PaymentResponseDto> getPaymentsByUserId(
            @RequestParam Long userId, Pageable pageable) {
        return paymentService.getPaymentsByUserId(userId, pageable);
    }

    @PostMapping("/single-lesson")
    public PaymentResponseDto createLessonPayment(
            @RequestParam Long userId, @RequestParam Long bookingId) {
        return paymentService.createSingleLessonPayment(userId, bookingId);
    }

    @GetMapping("/success")
    public PaymentResponseDto fulfillPayment(@RequestParam String sessionId) {
        return paymentService.fulfillPayment(sessionId);
    }

    @GetMapping("/cancel")
    public String handleCancel() {
        return paymentService.handleCancel();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        paymentService.processWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
