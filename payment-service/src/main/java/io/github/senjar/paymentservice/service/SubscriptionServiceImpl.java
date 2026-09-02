package io.github.senjar.paymentservice.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.github.senjar.paymentservice.dto.CheckoutSessionResponseDto;
import io.github.senjar.paymentservice.dto.SubscriptionStatusDto;
import io.github.senjar.paymentservice.model.Subscription;
import io.github.senjar.paymentservice.model.SubscriptionStatus;
import io.github.senjar.paymentservice.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public CheckoutSessionResponseDto createCheckoutSession(Long studentId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("studentId", studentId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("pln")
                                .setUnitAmount(9900L)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("LinguaHub — 1 miesiąc dostępu")
                                        .build())
                                .build())
                        .build())
                .build();

        Session session = Session.create(params);
        return new CheckoutSessionResponseDto(session.getUrl());
    }

    public void handleWebhook(String payload, String sigHeader) throws Exception {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new IllegalStateException("Cannot deserialize session"));

            Long studentId = Long.valueOf(session.getMetadata().get("studentId"));
            activateSubscription(studentId);
        }
    }

    private void activateSubscription(Long studentId) {
        Subscription subscription = subscriptionRepository.findByStudentId(studentId)
                .orElse(Subscription.builder().studentId(studentId).build());

        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        subscription.setActivatedAt(LocalDateTime.now());
        subscription.setExpiresAt(LocalDateTime.now().plusMonths(1));

        subscriptionRepository.save(subscription);
    }

    public SubscriptionStatusDto getStatus(Long studentId) {
        boolean active = subscriptionRepository.findByStudentId(studentId)
                .map(s -> s.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                        && s.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
        return new SubscriptionStatusDto(active);
    }
}
