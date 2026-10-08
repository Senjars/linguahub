package io.github.senjar.bookingservice.controller;

import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.security.CurrentUserId;
import io.github.senjar.bookingservice.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Slots", description = "Lesson slots created and managed by teachers")
@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @Operation(
            summary = "Create a slot",
            description = "Creates a new lesson slot owned by the authenticated teacher."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Slot created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "409",
                    description = "Teacher already has a slot starting at this time")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponseDto createSlot(@CurrentUserId UUID userId,
                                      @RequestBody @Valid CreateSlotDto createSlotDto) {
        return slotService.createSlot(userId, createSlotDto);
    }

    @Operation(
            summary = "Delete a slot",
            description = "Deletes a slot. Only the owning teacher can delete it, "
                    + "and only if nobody has booked it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Slot deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Slot belongs to another teacher"),
            @ApiResponse(responseCode = "404", description = "Slot not found"),
            @ApiResponse(responseCode = "409", description = "Slot has active bookings")
    })
    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@CurrentUserId UUID userId, @PathVariable Long slotId) {
        slotService.deleteSlot(userId, slotId);
    }

    @Operation(
            summary = "List all slots",
            description = "Returns every slot. The list is empty when there are none."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slots returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    })
    @GetMapping
    public List<SlotResponseDto> getAllSlots() {
        return slotService.getAllSlots();
    }

    @Operation(summary = "Get a slot by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @GetMapping("/{slotId}")
    public SlotResponseDto getSlotById(@PathVariable Long slotId) {
        return slotService.getSlotById(slotId);
    }
}
