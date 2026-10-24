package com.example.taketoday.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Журнал событий (п. 5.3.4 ТЗ): подтверждение и ошибки оплаты, поздняя оплата, повторные сообщения.
 * Пишем человеческим языком, без данных карты.
 */
@Repository
public class AuditLog {

    public static final String PAYMENT_STARTED = "PAYMENT_STARTED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_DUPLICATE = "PAYMENT_DUPLICATE";
    public static final String LATE_PAYMENT = "LATE_PAYMENT";
    public static final String PAYMENT_UNAVAILABLE = "PAYMENT_UNAVAILABLE";
    public static final String ERROR = "ERROR";

    private final JdbcTemplate db;

    public AuditLog(JdbcTemplate db) {
        this.db = db;
    }

    public void log(String eventType, UUID orderId, UUID paymentId, UUID userId, String details) {
        db.update("""
                        INSERT INTO audit_log (id, event_type, order_id, payment_id, user_id, details, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), eventType, orderId, paymentId, userId,
                details.length() > 1000 ? details.substring(0, 1000) : details, Timestamp.from(Instant.now()));
    }
}
