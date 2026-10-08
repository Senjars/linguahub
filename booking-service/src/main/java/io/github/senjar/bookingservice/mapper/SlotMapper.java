package io.github.senjar.bookingservice.mapper;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.model.slot.Slot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SlotMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "teacherId", ignore = true)
    @Mapping(target = "bookedCount", ignore = true)
    Slot toEntity(CreateSlotDto createSlotDto);

    SlotResponseDto toDto(Slot slot);
}
