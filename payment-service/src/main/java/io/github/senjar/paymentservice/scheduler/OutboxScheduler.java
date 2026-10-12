package io.github.senjar.paymentservice.scheduler;

import io.github.senjar.paymentservice.event.PaymentConfirmedEvent;
import io.github.senjar.paymentservice.model.outbox.Outbox;
import io.github.senjar.paymentservice.publisher.PaymentEventPublisher;
import io.github.senjar.paymentservice.repository.OutboxRepository;
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
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    @Scheduled(fixedDelay = 2000)
    public void processOutboxMessages() {
        List<Outbox> messages = outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(5);
        for (Outbox msg : messages) {
            try {
                PaymentConfirmedEvent event =
                        objectMapper.readValue(msg.getPayload(), PaymentConfirmedEvent.class);

                paymentEventPublisher.publishPaymentConfirmed(event.bookingId());

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

