package com.example.taketoday.payment;

import java.time.Instant;
import java.util.UUID;

/** Строка таблицы payments. Полного номера карты здесь нет и быть не может — только последние 4 цифры. */
public record Payment(UUID id, UUID orderId, int amount, PaymentStatus status, String providerPaymentId,
                      String cardLast4, String errorMessage, Instant createdAt, Instant completedAt) {
}
