package com.example.taketoday.payment;

/** Платёжная служба не ответила. Бронь при этом не трогаем: покупатель повторяет попытку, пока идёт таймер. */
public class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException(String message) {
        super(message);
    }
}
