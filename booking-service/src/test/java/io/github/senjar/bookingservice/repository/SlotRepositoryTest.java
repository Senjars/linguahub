package io.github.senjar.bookingservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.senjar.bookingservice.config.TestcontainersConfiguration;
import io.github.senjar.bookingservice.model.slot.Slot;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@DataJpaTest
@Transactional
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class SlotRepositoryTest {

    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 25, 11, 0);

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        slotRepository.deleteAll();
    }

    @Test
    @DisplayName("Should increment bookedCount and return 1 when the slot has free places")
    void tryReserveSlot_slotWithFreePlaces_incrementsBookedCountAndReturnsOne() {
        // GIVEN
        Slot slot = slotRepository.saveAndFlush(createSlot(2, 0));

        // WHEN
        int reserved = slotRepository.tryReserveSlot(slot.getId());
        entityManager.clear();

        // THEN
        assertThat(reserved).isEqualTo(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Should return 0 and keep bookedCount unchanged when the slot is full")
    void tryReserveSlot_fullSlot_returnsZeroAndKeepsBookedCount() {
        // GIVEN
        Slot slot = slotRepository.saveAndFlush(createSlot(1, 1));

        // WHEN
        int reserved = slotRepository.tryReserveSlot(slot.getId());
        entityManager.clear();

        // THEN
        assertThat(reserved).isZero();
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Should return 0 when the slot does not exist")
    void tryReserveSlot_nonExistingSlot_returnsZero() {
        // GIVEN
        Long nonExistingId = 999_999L;

        // WHEN
        int reserved = slotRepository.tryReserveSlot(nonExistingId);

        // THEN
        assertThat(reserved).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Should never exceed the capacity under concurrent reservations")
    void tryReserveSlot_concurrentAttempts_neverExceedsCapacity() throws Exception {
        // GIVEN
        int capacity = 3;
        int attempts = 8;
        Slot slot = slotRepository.saveAndFlush(createSlot(capacity, 0));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);

        // WHEN
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                startSignal.await();
                return transaction.execute(status -> slotRepository.tryReserveSlot(slot.getId()));
            }));
        }
        startSignal.countDown();
        int successes = 0;
        for (Future<Integer> result : results) {
            successes += result.get();
        }
        executor.shutdown();

        // THEN
        assertThat(successes).isEqualTo(capacity);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getBookedCount())
                .isEqualTo(capacity);
    }

    @Test
    @DisplayName("Should detect a slot of the same teacher with the same time")
    void existsOverlapping_sameTime_returnsTrue() {
        // GIVEN
        slotRepository.saveAndFlush(createSlot(TEACHER_ID, START, END));

        // WHEN
        boolean overlapping = slotRepository.existsOverlapping(TEACHER_ID, START, END);

        // THEN
        assertThat(overlapping).isTrue();
    }

    @Test
    @DisplayName("Should detect a slot of the same teacher that overlaps only partially")
    void existsOverlapping_partiallyOverlappingTime_returnsTrue() {
        // GIVEN
        slotRepository.saveAndFlush(createSlot(TEACHER_ID, START, END));

        // WHEN
        boolean overlapping = slotRepository.existsOverlapping(
                TEACHER_ID, START.plusMinutes(30), END.plusMinutes(30));

        // THEN
        assertThat(overlapping).isTrue();
    }

    @Test
    @DisplayName("Should allow a slot that starts exactly when the previous one ends")
    void existsOverlapping_adjacentSlot_returnsFalse() {
        // GIVEN
        slotRepository.saveAndFlush(createSlot(TEACHER_ID, START, END));

        // WHEN
        boolean overlapping = slotRepository.existsOverlapping(TEACHER_ID, END, END.plusHours(1));

        // THEN
        assertThat(overlapping).isFalse();
    }

    @Test
    @DisplayName("Should ignore slots of other teachers")
    void existsOverlapping_sameTimeButOtherTeacher_returnsFalse() {
        // GIVEN
        slotRepository.saveAndFlush(createSlot(TEACHER_ID, START, END));

        // WHEN
        boolean overlapping = slotRepository.existsOverlapping(UUID.randomUUID(), START, END);

        // THEN
        assertThat(overlapping).isFalse();
    }

    private Slot createSlot(int capacity, int bookedCount) {
        return Slot.builder()
                .teacherId(UUID.randomUUID())
                .startTime(START)
                .endTime(END)
                .capacity(capacity)
                .bookedCount(bookedCount)
                .build();
    }

    private Slot createSlot(UUID teacherId, LocalDateTime startTime, LocalDateTime endTime) {
        return Slot.builder()
                .teacherId(teacherId)
                .startTime(startTime)
                .endTime(endTime)
                .capacity(1)
                .bookedCount(0)
                .build();
    }
}
