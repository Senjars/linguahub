package io.github.senjar.bookingservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.senjar.bookingservice.config.TestcontainersConfiguration;
import io.github.senjar.bookingservice.model.outbox.EventType;
import io.github.senjar.bookingservice.model.outbox.Outbox;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OutboxRepositoryTest {

    private static final int RETRY_LIMIT = 5;
    private static final int PAGE_LIMIT = 50;

    @Autowired
    private OutboxRepository outboxRepository;

    @Nested
    @DisplayName("findTop50ByProcessedFalseAndRetryCountLessThan")
    class FindTop50ByProcessedFalseAndRetryCountLessThan {

        @Test
        @DisplayName("Should return an unprocessed message that has not used its retries")
        void findTop50_unprocessedMessage_isReturned() {
            // GIVEN
            Outbox message = saveOutbox(false, 0);

            // WHEN
            List<Outbox> result =
                    outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT);

            // THEN
            assertThat(result).extracting(Outbox::getId).containsExactly(message.getId());
        }

        @Test
        @DisplayName("Should not return a message that was already processed")
        void findTop50_processedMessage_isNotReturned() {
            // GIVEN
            saveOutbox(true, 0);

            // WHEN
            List<Outbox> result =
                    outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT);

            // THEN
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should still return a message with one retry left")
        void findTop50_messageBelowRetryLimit_isReturned() {
            // GIVEN
            saveOutbox(false, RETRY_LIMIT - 1);

            // WHEN
            List<Outbox> result =
                    outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT);

            // THEN
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should not return a message that used all its retries")
        void findTop50_messageAtRetryLimit_isNotReturned() {
            // GIVEN
            saveOutbox(false, RETRY_LIMIT);

            // WHEN
            List<Outbox> result =
                    outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT);

            // THEN
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return at most 50 messages per call")
        void findTop50_moreThanFiftyMessages_returnsOnlyFifty() {
            // GIVEN
            List<Outbox> messages = new ArrayList<>();
            for (int i = 0; i < PAGE_LIMIT + 5; i++) {
                messages.add(provideOutbox(false, 0));
            }
            outboxRepository.saveAll(messages);

            // WHEN
            List<Outbox> result =
                    outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT);

            // THEN
            assertThat(result).hasSize(PAGE_LIMIT);
        }
    }

    private Outbox saveOutbox(boolean processed, int retryCount) {
        return outboxRepository.save(provideOutbox(processed, retryCount));
    }

    private Outbox provideOutbox(boolean processed, int retryCount) {
        Outbox outbox = new Outbox();
        outbox.setEventType(EventType.BOOKING_CONFIRMED);
        outbox.setPayload("{\"bookingId\":1}");
        outbox.setProcessed(processed);
        outbox.setRetryCount(retryCount);
        return outbox;
    }
}
