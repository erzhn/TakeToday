package com.example.taketoday.refund;

/** Кто отменил оплаченный заказ (reservation_orders.cancel_reason, SPEC раздел 6). */
public enum CancelReason {
    /** Покупатель — до начала выдачи. Порции возвращаются в продажу. */
    CUSTOMER(true),
    /** Заведение не может выдать. Порции в продажу не возвращаем — их нет. */
    VENUE(false),
    /** Администратор. В продажу не возвращаем: заказ отменяют из-за проблемы. */
    ADMIN(false);

    private final boolean returnToSale;

    CancelReason(boolean returnToSale) {
        this.returnToSale = returnToSale;
    }

    public boolean returnsToSale() {
        return returnToSale;
    }
}
