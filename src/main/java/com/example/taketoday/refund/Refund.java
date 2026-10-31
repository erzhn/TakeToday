package com.example.taketoday.refund;

import java.time.Instant;
import java.util.UUID;

/** Строка таблицы refunds. */
public record Refund(UUID id, UUID orderId, UUID paymentId, int amount, RefundStatus status, String initiatedBy,
                     UUID adminId, String reason, String providerRefundId, String errorMessage, int attempts,
                     Instant createdAt, Instant completedAt) {
}
