package com.paysense.payment.exception;

public class DuplicatePaymentException extends PaymentException {

    public DuplicatePaymentException(String message) {
        super(message);
    }
}
