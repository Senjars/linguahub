package io.github.senjar.bookingservice.service;

import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventPublisher {

    private static final String TOPIC = "booking-confirmed";

    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        kafkaTemplate.send(TOPIC, event.bookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish BookingConfirmedEvent for booking {}",
                                event.bookingId(), ex);
                    } else {
                        log.info("Published BookingConfirmedEvent for booking {}",
                                event.bookingId());
                    }
                });
    }
}
