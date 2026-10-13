package io.github.senjar.paymentservice.exception;

public class PaymentNotPaidException extends RuntimeException {
    public PaymentNotPaidException(String message) {
        super(message);
    }
}
