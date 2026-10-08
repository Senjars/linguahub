package io.github.senjar.bookingservice.service;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import java.util.List;
import java.util.UUID;

public interface SlotService {

    void deleteSlot(UUID teacherId, Long slotId);

    SlotResponseDto getSlotById(Long slotId);

    SlotResponseDto createSlot(UUID teacherId, CreateSlotDto createSlotDto);

    List<SlotResponseDto> getAllSlots();
}
