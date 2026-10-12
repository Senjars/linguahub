package io.github.senjar.bookingservice.exception;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException(String message) {
        super(message);
    }
}
