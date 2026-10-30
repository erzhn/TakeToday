package com.example.taketoday.pickup;

/** Выдать заказ нельзя — текст для сотрудника. */
public class PickupException extends RuntimeException {
    public PickupException(String message) {
        super(message);
    }
}
