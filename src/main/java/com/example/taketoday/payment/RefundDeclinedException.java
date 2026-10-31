package com.example.taketoday.payment;

/** Платёжная служба отказала в возврате. Автоматически не повторяем — решает администратор. */
public class RefundDeclinedException extends RuntimeException {
    public RefundDeclinedException(String message) {
        super(message);
    }
}
