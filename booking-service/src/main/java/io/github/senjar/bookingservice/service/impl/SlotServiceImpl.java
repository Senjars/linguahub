package io.github.senjar.bookingservice.service.impl;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.exception.AccessDeniedException;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotHasBookingsException;
import io.github.senjar.bookingservice.mapper.SlotMapper;
import io.github.senjar.bookingservice.model.slot.Slot;
import io.github.senjar.bookingservice.repository.SlotRepository;
import io.github.senjar.bookingservice.service.SlotService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final SlotMapper slotMapper;

    @Override
    @Transactional
    public void deleteSlot(UUID teacherId,Long slotId) {
        Slot slot = getSlotOrThrow(slotId);

        if (!Objects.equals(slot.getTeacherId(), teacherId)) {
            log.warn("Teacher {} tried to delete slot {} owned by {}",
                    teacherId, slotId, slot.getTeacherId());

            throw new AccessDeniedException("You cannot remove slot with id: " + slotId);
        }

        if (slot.getBookedCount() > 0) {
            throw new SlotHasBookingsException("Cannot delete slot with active bookings");
        }

        slotRepository.delete(slot);

        log.info("Slot {} deleted (teacher {})", slotId, slot.getTeacherId());
    }

    @Override
    @Transactional
    public SlotResponseDto createSlot(UUID teacherId, CreateSlotDto createSlotDto) {
        Slot slot = slotMapper.toEntity(createSlotDto);
        slot.setTeacherId(teacherId);

        Slot savedSlot = slotRepository.save(slot);

        log.info("Slot {} created by teacher {}", savedSlot.getId(), teacherId);

        return slotMapper.toDto(savedSlot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponseDto> getAllSlots() {
        return slotRepository.findAll().stream()
                        .map(slotMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponseDto getSlotById(Long slotId) {
        Slot slot = getSlotOrThrow(slotId);
        return slotMapper.toDto(slot);
    }

    private Slot getSlotOrThrow(Long slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found with id: "
                        + slotId));
    }
}
