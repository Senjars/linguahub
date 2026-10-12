package io.github.senjar.bookingservice.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
import io.github.senjar.bookingservice.model.outbox.EventType;
import io.github.senjar.bookingservice.model.outbox.Outbox;
import io.github.senjar.bookingservice.publisher.BookingEventPublisher;
import io.github.senjar.bookingservice.repository.OutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    private static final int RETRY_LIMIT = 5;
    private static final Long BOOKING_ID = 42L;
    private static final String EMAIL = "student@example.com";
    private static final String VALID_PAYLOAD = """
            {"bookingId":42,"slotId":5,"studentEmail":"student@example.com",\
            "startTime":"2026-10-25T10:00:00","endTime":"2026-10-25T11:00:00"}""";
    private static final String BROKEN_PAYLOAD = "this is not json";

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    private OutboxScheduler outboxScheduler;

    @BeforeEach
    void setUp() {
        outboxScheduler = new OutboxScheduler(
                outboxRepository, bookingEventPublisher, JsonMapper.builder().build());
    }

    @Nested
    @DisplayName("processOutboxMessages")
    class ProcessOutboxMessages {

        @Test
        @DisplayName("Should publish the event from the payload and mark the message processed")
        void processOutboxMessages_unprocessedMessage_publishesEventAndMarksProcessed() {
            // GIVEN
            Outbox message = provideOutbox(VALID_PAYLOAD);
            when(outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT))
                    .thenReturn(List.of(message));

            // WHEN
            outboxScheduler.processOutboxMessages();

            // THEN
            ArgumentCaptor<BookingConfirmedEvent> captor =
                    ArgumentCaptor.forClass(BookingConfirmedEvent.class);
            verify(bookingEventPublisher).publishBookingConfirmed(captor.capture());
            BookingConfirmedEvent published = captor.getValue();
            assertThat(published.bookingId()).isEqualTo(BOOKING_ID);
            assertThat(published.studentEmail()).isEqualTo(EMAIL);
            assertThat(published.startTime()).isEqualTo(LocalDateTime.of(2026, 10, 25, 10, 0));
            assertThat(message.getProcessed()).isTrue();
            verify(outboxRepository).save(message);
        }

        @Test
        @DisplayName("Should keep the message unprocessed and count a retry when publishing fails")
        void processOutboxMessages_publisherFails_incrementsRetryCountAndKeepsUnprocessed() {
            // GIVEN
            Outbox message = provideOutbox(VALID_PAYLOAD);
            when(outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT))
                    .thenReturn(List.of(message));
            doThrow(new IllegalStateException("Kafka unavailable"))
                    .when(bookingEventPublisher).publishBookingConfirmed(any(BookingConfirmedEvent.class));

            // WHEN
            outboxScheduler.processOutboxMessages();

            // THEN
            assertThat(message.getProcessed()).isFalse();
            assertThat(message.getRetryCount()).isEqualTo(1);
            verify(outboxRepository).save(message);
        }

        @Test
        @DisplayName("Should count a retry without publishing when the payload is not valid JSON")
        void processOutboxMessages_brokenPayload_incrementsRetryCountWithoutPublishing() {
            // GIVEN
            Outbox message = provideOutbox(BROKEN_PAYLOAD);
            when(outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT))
                    .thenReturn(List.of(message));

            // WHEN
            outboxScheduler.processOutboxMessages();

            // THEN
            assertThat(message.getProcessed()).isFalse();
            assertThat(message.getRetryCount()).isEqualTo(1);
            verifyNoInteractions(bookingEventPublisher);
        }

        @Test
        @DisplayName("Should still process the remaining messages when one of them fails")
        void processOutboxMessages_oneMessageFails_otherMessagesAreStillProcessed() {
            // GIVEN
            Outbox broken = provideOutbox(BROKEN_PAYLOAD);
            Outbox valid = provideOutbox(VALID_PAYLOAD);
            when(outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT))
                    .thenReturn(List.of(broken, valid));

            // WHEN
            outboxScheduler.processOutboxMessages();

            // THEN
            assertThat(broken.getProcessed()).isFalse();
            assertThat(broken.getRetryCount()).isEqualTo(1);
            assertThat(valid.getProcessed()).isTrue();
            verify(bookingEventPublisher).publishBookingConfirmed(any(BookingConfirmedEvent.class));
        }

        @Test
        @DisplayName("Should do nothing when there are no messages to process")
        void processOutboxMessages_noMessages_doesNothing() {
            // GIVEN
            when(outboxRepository.findTop50ByProcessedFalseAndRetryCountLessThan(RETRY_LIMIT))
                    .thenReturn(List.of());

            // WHEN
            outboxScheduler.processOutboxMessages();

            // THEN
            verifyNoInteractions(bookingEventPublisher);
            verify(outboxRepository, never()).save(any(Outbox.class));
        }
    }

    private Outbox provideOutbox(String payload) {
        Outbox outbox = new Outbox();
        outbox.setEventType(EventType.BOOKING_CONFIRMED);
        outbox.setPayload(payload);
        return outbox;
    }
}
