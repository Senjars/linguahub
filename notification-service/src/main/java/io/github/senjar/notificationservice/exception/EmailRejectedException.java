package io.github.senjar.notificationservice.exception;

public class EmailRejectedException extends RuntimeException {

    public EmailRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
