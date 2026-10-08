package io.github.senjar.bookingservice.exception;

public class SlotHasBookingsException extends RuntimeException {
    public SlotHasBookingsException(String message) {
        super(message);
    }
}
