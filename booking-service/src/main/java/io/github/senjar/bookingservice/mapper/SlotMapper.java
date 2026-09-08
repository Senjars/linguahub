package io.github.senjar.bookingservice.mapper;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.model.slot.Slot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SlotMapper {

    Slot toEntity(CreateSlotDto createSlotDto);

    SlotResponseDto toDto(Slot slot);
}
