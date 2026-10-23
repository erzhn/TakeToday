package com.example.taketoday.reservation;

import com.example.taketoday.common.AppTime;
import java.time.Instant;
import java.util.UUID;

public record Reservation(UUID id, UUID offerId, String title, String establishmentName, String address,
                          int amount, int quantity, int unitPrice, Instant pickupStart, Instant pickupEnd, String status,
                          Instant createdAt, Instant expiresAt) {
    public String getStatusText() {
        return switch (status) {
            case "AWAITING_PAYMENT" -> "Ожидает оплаты";
            case "PAID" -> "Оплачен";
            default -> "Отменен";
        };
    }
    public String getPickupStartText() { return AppTime.format(pickupStart); }
    public String getPickupEndText() { return AppTime.format(pickupEnd); }
}
