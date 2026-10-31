package com.example.taketoday.refund;

/** Отменить заказ нельзя — текст для того, кто нажал кнопку. */
public class CancelException extends RuntimeException {
    public CancelException(String message) {
        super(message);
    }
}
