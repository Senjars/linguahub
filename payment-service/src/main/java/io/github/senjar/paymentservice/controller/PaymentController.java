package io.github.senjar.paymentservice.controller;

import com.stripe.exception.StripeException;
import io.github.senjar.paymentservice.dto.CheckoutSessionResponseDto;
import io.github.senjar.paymentservice.dto.SubscriptionStatusDto;
import io.github.senjar.paymentservice.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/checkout-session")
    public CheckoutSessionResponseDto createCheckoutSession(@RequestParam Long studentId) throws
            StripeException {
        return subscriptionService.createCheckoutSession(studentId);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) throws Exception {
        subscriptionService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscriptions/{studentId}/status")
    public SubscriptionStatusDto getStatus(@PathVariable Long studentId) {
        return subscriptionService.getStatus(studentId);
    }
}
