package io.github.senjar.bookingservice.controller;

import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.security.CurrentUserEmail;
import io.github.senjar.bookingservice.security.CurrentUserId;
import io.github.senjar.bookingservice.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookings", description = "Lesson bookings made by students")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Book a lesson",
            description = "Reserves a place in the slot and creates a PENDING booking together "
                    + "with a payment session. The response contains sessionUrl, the Stripe "
                    + "Checkout link the student has to be redirected to. The booking becomes "
                    + "CONFIRMED only after the payment succeeds."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Booking created, sessionUrl points to the payment page"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "409", description = "Slot is full or does not exist")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponseDto bookLesson(
            @RequestBody @Valid BookingRequestDto bookingRequestDto,
            @CurrentUserId UUID userId,
            @CurrentUserEmail String email) {
        return bookingService.createBooking(bookingRequestDto, userId, email);
    }

    @Operation(
            summary = "Get a booking by id",
            description = "Returns the booking only if it belongs to the authenticated student. "
                    + "sessionUrl is null here, it is returned only when the booking is created."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "404",
                    description = "Booking not found or belongs to another student")
    })
    @GetMapping("/{id}")
    public BookingResponseDto getBookingById(
            @PathVariable Long id,
            @CurrentUserId UUID userId) {
        return bookingService.findBooking(id, userId);
    }

    @Operation(
            summary = "List my bookings",
            description = "Returns all bookings of the authenticated student. "
                    + "The list is empty when there are none."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookings returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    })
    @GetMapping
    public List<BookingResponseDto> getAllBookingsForStudent(
            @CurrentUserId UUID userId) {
        return bookingService.getBookingsForStudent(userId);
    }
}
