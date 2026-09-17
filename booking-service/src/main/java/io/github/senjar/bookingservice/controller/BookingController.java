package io.github.senjar.bookingservice.controller;

import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.booking.UpdateBookingDto;
import io.github.senjar.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponseDto createBooking(
            @RequestBody @Valid BookingRequestDto bookingRequestDto,
            @AuthenticationPrincipal Long userId) {
        return bookingService.createBooking(bookingRequestDto, userId);
    }

    @GetMapping("/{id}")
    public BookingResponseDto getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return bookingService.findBooking(id, userId);
    }

    @GetMapping
    public List<BookingResponseDto> getAllBookingsForStudent(
            @AuthenticationPrincipal Long userId) {
        return bookingService.getBookingsForStudent(userId);
    }

    @PatchMapping("/{id}")
    public BookingResponseDto updateBooking(
            @PathVariable Long id,
            @RequestBody @Valid UpdateBookingDto updateBookingDto,
            @AuthenticationPrincipal Long userId) {
        return bookingService.updateBooking(updateBookingDto, id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        bookingService.cancelBooking(id, userId);
    }

    @PatchMapping("/{bookingId}/confirm")
    public ResponseEntity<Void> confirmBooking(@PathVariable Long bookingId) {
        bookingService.confirmBooking(bookingId);
        return ResponseEntity.ok().build();
    }
}
