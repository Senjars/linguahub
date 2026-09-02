package io.github.senjar.courseservice.exception;

public class AccessDeniedException extends RuntimeException
{
    public AccessDeniedException(String message) {
        super(message);
    }
}
