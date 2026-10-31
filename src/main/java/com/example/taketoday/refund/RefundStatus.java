package com.example.taketoday.refund;

/** Состояние возврата (оно же — reservation_orders.refund_status, кроме NONE). */
public enum RefundStatus {
    PENDING, COMPLETED, FAILED
}
