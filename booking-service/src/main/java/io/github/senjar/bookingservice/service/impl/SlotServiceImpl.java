package io.github.senjar.bookingservice.service.impl;

import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.mapper.SlotMapper;
import io.github.senjar.bookingservice.model.slot.Slot;
import io.github.senjar.bookingservice.repository.SlotRepository;
import io.github.senjar.bookingservice.service.SlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final SlotMapper slotMapper;

    @Override
    public void deleteSlot(Long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));

        slotRepository.delete(slot);
    }

    @Override
    public SlotResponseDto getSlotById(Long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found with id: " + slotId));

        return slotMapper.toDto(slot);
    }

    @Override
    public SlotResponseDto createSlot(Long teacherId, SlotResponseDto slotResponseDto) {

        return null;
    }

    @Override
    public List<SlotResponseDto> getAllSlots() {
        return List.of();
    }
}
