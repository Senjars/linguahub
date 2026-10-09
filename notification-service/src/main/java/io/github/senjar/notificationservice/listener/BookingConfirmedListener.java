package io.github.senjar.notificationservice.listener;

import io.github.senjar.notificationservice.dto.EmailRequestDto;
import io.github.senjar.notificationservice.event.BookingConfirmedEvent;
import io.github.senjar.notificationservice.service.EmailService;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmedListener {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final EmailService emailService;

    @KafkaListener(topics = "booking-confirmed")
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received BookingConfirmedEvent for booking{}", event.bookingId());

        if (event.studentEmail() == null || event.studentEmail().isBlank()) {
            log.warn("Booking {} has no student email, skipping notification", event.bookingId());
            return;
        }

        String subject = "Rezerwacja potwierdzona";
        String htmlBody = "<h2>Dziękujemy za rezerwację!</h2>"
                + "<p>Twoja lekcja: " + event.startTime().format(FORMAT)
                + " - " + event.endTime().format(FORMAT) + ".</p>"
                + "<p>Numer rezerwacji: " + event.bookingId() + "</p>";

        emailService.sendEmail(new EmailRequestDto(event.studentEmail(), subject, htmlBody));
    }
}
