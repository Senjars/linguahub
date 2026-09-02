package io.github.senjar.paymentservice.service;

import com.stripe.exception.StripeException;
import io.github.senjar.paymentservice.dto.CheckoutSessionResponseDto;
import io.github.senjar.paymentservice.dto.SubscriptionStatusDto;

public interface SubscriptionService {

    CheckoutSessionResponseDto createCheckoutSession(Long studentId) throws StripeException;

    void handleWebhook(String payload, String sigHeader) throws Exception;

    SubscriptionStatusDto getStatus(Long studentId);
}
