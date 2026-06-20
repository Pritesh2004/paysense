package com.paysense.payment.exception;

public class AccountNotFoundException extends PaymentException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
