package io.github.senjar.bookingservice.scheduler;

import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
import io.github.senjar.bookingservice.model.outbox.Outbox;
import io.github.senjar.bookingservice.publisher.BookingEventPublisher;
import io.github.senjar.bookingservice.repository.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    @Scheduled(fixedDelay = 2000)
    public void processOutboxMessages() {
        List<Outbox> messages = outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(5);
        for (Outbox msg : messages) {
            try {
                BookingConfirmedEvent event =
                        objectMapper.readValue(msg.getPayload(), BookingConfirmedEvent.class);

                bookingEventPublisher.publishBookingConfirmed(event);

                msg.setProcessed(true);
                outboxRepository.save(msg);
            } catch (Exception e) {
                log.error("Failed to process outbox message id: {}", msg.getId(), e);

                msg.setRetryCount(msg.getRetryCount() + 1);
                outboxRepository.save(msg);
            }
        }
    }
}
