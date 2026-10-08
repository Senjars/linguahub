package io.github.senjar.bookingservice.listener;

import io.github.senjar.bookingservice.event.PaymentConfirmedEvent;
import io.github.senjar.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmedListener {

    private final BookingService bookingService;

    @KafkaListener(topics = "payment-confirmed", groupId = "booking-service")
    public void handlePaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("Received PaymentConfirmedEvent for booking {}", event.bookingId());
        bookingService.confirmBooking(event.bookingId());
    }
}
