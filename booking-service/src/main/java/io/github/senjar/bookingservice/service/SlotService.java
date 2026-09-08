package io.github.senjar.bookingservice.service;

import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import java.util.List;

public interface SlotService {

    void deleteSlot(Long slotId);

    SlotResponseDto getSlotById(Long slotId);

    SlotResponseDto createSlot(Long teacherId, SlotResponseDto slotResponseDto);

    List<SlotResponseDto> getAllSlots();
}
