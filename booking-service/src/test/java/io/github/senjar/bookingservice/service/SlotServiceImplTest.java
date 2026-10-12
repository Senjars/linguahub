package io.github.senjar.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.exception.AccessDeniedException;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotHasBookingsException;
import io.github.senjar.bookingservice.exception.SlotOverlapException;
import io.github.senjar.bookingservice.mapper.SlotMapper;
import io.github.senjar.bookingservice.model.slot.Slot;
import io.github.senjar.bookingservice.repository.SlotRepository;
import io.github.senjar.bookingservice.service.impl.SlotServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SlotServiceImplTest {

    private static final Long SLOT_ID = 1L;
    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private SlotMapper slotMapper;

    @InjectMocks
    private SlotServiceImpl slotService;

    @Nested
    @DisplayName("createSlot")
    class CreateSlot {

        @Test
        @DisplayName("Should save the mapped slot and return its DTO")
        void createSlot_validRequest_returnsSlotDto() {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();
            Slot slot = provideSlot();
            SlotResponseDto expected = provideSlotResponse();
            when(slotMapper.toEntity(request)).thenReturn(slot);
            when(slotRepository.save(slot)).thenReturn(slot);
            when(slotMapper.toDto(slot)).thenReturn(expected);

            // WHEN
            SlotResponseDto result = slotService.createSlot(TEACHER_ID, request);

            // THEN
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should always use the teacher id from the token, not the one from the mapper")
        void createSlot_mappedSlotWithOtherTeacher_savesWithTeacherIdFromArgument() {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();
            Slot mappedSlot = provideSlot(UUID.randomUUID(), 0);
            when(slotMapper.toEntity(request)).thenReturn(mappedSlot);
            when(slotRepository.save(any(Slot.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(slotMapper.toDto(any(Slot.class))).thenReturn(provideSlotResponse());

            // WHEN
            slotService.createSlot(TEACHER_ID, request);

            // THEN
            ArgumentCaptor<Slot> captor = ArgumentCaptor.forClass(Slot.class);
            verify(slotRepository).save(captor.capture());
            assertThat(captor.getValue().getTeacherId()).isEqualTo(TEACHER_ID);
        }

        @Test
        @DisplayName("Should throw SlotOverlapException when "
                + "the teacher already has a slot at that time")
        void createSlot_teacherAlreadyHasOverlappingSlot_throwsSlotOverlapException() {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();
            when(slotRepository.existsOverlapping(TEACHER_ID, START, END)).thenReturn(true);

            // WHEN / THEN
            assertThatThrownBy(() -> slotService.createSlot(TEACHER_ID, request))
                    .isInstanceOf(SlotOverlapException.class);
            verify(slotRepository, never()).save(any(Slot.class));
            verifyNoInteractions(slotMapper);
        }
    }

    @Nested
    @DisplayName("deleteSlot")
    class DeleteSlot {

        @Test
        @DisplayName("Should delete the slot when it belongs to the teacher and has no bookings")
        void deleteSlot_ownSlotWithoutBookings_deletesSlot() {
            // GIVEN
            Slot slot = provideSlot();
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

            // WHEN
            slotService.deleteSlot(TEACHER_ID, SLOT_ID);

            // THEN
            verify(slotRepository).delete(slot);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when the slot does not exist")
        void deleteSlot_nonExistingSlot_throwsEntityNotFoundException() {
            // GIVEN
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> slotService.deleteSlot(TEACHER_ID, SLOT_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(String.valueOf(SLOT_ID));
            verify(slotRepository, never()).delete(any(Slot.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when the slot belongs to another teacher")
        void deleteSlot_slotOfAnotherTeacher_throwsAccessDeniedException() {
            // GIVEN
            Slot slot = provideSlot(UUID.randomUUID(), 0);
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

            // WHEN / THEN
            assertThatThrownBy(() -> slotService.deleteSlot(TEACHER_ID, SLOT_ID))
                    .isInstanceOf(AccessDeniedException.class);
            verify(slotRepository, never()).delete(any(Slot.class));
        }

        @Test
        @DisplayName("Should throw SlotHasBookingsException when the slot has active bookings")
        void deleteSlot_slotWithBookings_throwsSlotHasBookingsException() {
            // GIVEN
            Slot slot = provideSlot(TEACHER_ID, 1);
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

            // WHEN / THEN
            assertThatThrownBy(() -> slotService.deleteSlot(TEACHER_ID, SLOT_ID))
                    .isInstanceOf(SlotHasBookingsException.class);
            verify(slotRepository, never()).delete(any(Slot.class));
        }
    }

    @Nested
    @DisplayName("getAllSlots")
    class GetAllSlots {

        @Test
        @DisplayName("Should return all slots mapped to DTOs")
        void getAllSlots_slotsExist_returnsMappedList() {
            // GIVEN
            Slot slot = provideSlot();
            SlotResponseDto response = provideSlotResponse();
            when(slotRepository.findAll()).thenReturn(List.of(slot));
            when(slotMapper.toDto(slot)).thenReturn(response);

            // WHEN
            List<SlotResponseDto> result = slotService.getAllSlots();

            // THEN
            assertThat(result).containsExactly(response);
        }

        @Test
        @DisplayName("Should return an empty list when there are no slots")
        void getAllSlots_noSlots_returnsEmptyList() {
            // GIVEN
            when(slotRepository.findAll()).thenReturn(List.of());

            // WHEN
            List<SlotResponseDto> result = slotService.getAllSlots();

            // THEN
            assertThat(result).isEmpty();
            verifyNoInteractions(slotMapper);
        }
    }

    @Nested
    @DisplayName("getSlotById")
    class GetSlotById {

        @Test
        @DisplayName("Should return the DTO of an existing slot")
        void getSlotById_existingSlot_returnsSlotDto() {
            // GIVEN
            Slot slot = provideSlot();
            SlotResponseDto expected = provideSlotResponse();
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));
            when(slotMapper.toDto(slot)).thenReturn(expected);

            // WHEN
            SlotResponseDto result = slotService.getSlotById(SLOT_ID);

            // THEN
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when the slot does not exist")
        void getSlotById_nonExistingSlot_throwsEntityNotFoundException() {
            // GIVEN
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> slotService.getSlotById(SLOT_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(String.valueOf(SLOT_ID));
            verifyNoInteractions(slotMapper);
        }
    }

    private Slot provideSlot() {
        return provideSlot(TEACHER_ID, 0);
    }

    private Slot provideSlot(UUID teacherId, int bookedCount) {
        return Slot.builder()
                .id(SLOT_ID)
                .teacherId(teacherId)
                .startTime(START)
                .endTime(END)
                .capacity(1)
                .bookedCount(bookedCount)
                .build();
    }

    private SlotResponseDto provideSlotResponse() {
        return new SlotResponseDto(SLOT_ID, TEACHER_ID, START, END, 1, 0);
    }

    private CreateSlotDto provideCreateSlotDto() {
        return new CreateSlotDto(START, END, 1);
    }
}
