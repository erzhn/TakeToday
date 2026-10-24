package com.example.taketoday.payment;

/** Понятная покупателю причина, почему оплату сейчас начать или завершить нельзя. */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
