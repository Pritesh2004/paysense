package com.paysense.payment.exception;

public class InsufficientFundsException extends PaymentException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
