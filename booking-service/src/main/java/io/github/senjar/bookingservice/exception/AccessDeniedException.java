package io.github.senjar.bookingservice.exception;

public class AccessDeniedException extends RuntimeException
{
    public AccessDeniedException(String message) {
        super(message);
    }
}
