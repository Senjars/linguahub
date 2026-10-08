package io.github.senjar.paymentservice.service;

import io.github.senjar.paymentservice.event.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String TOPIC = "payment-confirmed";

    private final KafkaTemplate<String, PaymentConfirmedEvent> kafkaTemplate;

    public void publishPaymentConfirmed(Long bookingId) {
        kafkaTemplate.send(TOPIC, bookingId.toString(), new PaymentConfirmedEvent(bookingId));
        log.info("Published PaymentConfirmedEvent for booking {}", bookingId);
    }
}
