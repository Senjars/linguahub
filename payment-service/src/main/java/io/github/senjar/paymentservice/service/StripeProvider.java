package io.github.senjar.paymentservice.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.github.senjar.paymentservice.config.StripeConfig;
import io.github.senjar.paymentservice.exception.PaymentException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class StripeProvider {

    private final StripeConfig stripeConfig;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeConfig.getSecretKey();
    }

    public Session getSession(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            throw new PaymentException("Error retrieving payment session: " + e.getMessage());
        }
    }

    public Session createSession(Long userId, Long bookingId, BigDecimal amount, String description)
            throws StripeException {
        long amountInCents = amount.movePointRight(2).longValueExact();

        String baseUrl = stripeConfig.getAppUrl() + "/api/v1/payments";

        String successUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/success")
                .queryParam("sessionId", "{CHECKOUT_SESSION_ID}")
                .build().toUriString();

        String cancelUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/cancel")
                .build().toUriString();

        SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency("usd")
                .setUnitAmount(amountInCents)
                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Lesson Booking Payment")
                        .setDescription(description)
                        .build())
                .build();

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("bookingId", String.valueOf(bookingId))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build())
                .build();

        return Session.create(params);
    }

    public Event getWebhookEvent(String payload, String sigHeader)
            throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
    }
}
