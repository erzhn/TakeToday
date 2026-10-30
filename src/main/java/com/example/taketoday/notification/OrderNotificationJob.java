package com.example.taketoday.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class OrderNotificationJob {

    private static final long REMINDER_SECONDS = 30 * 60;

    private final JdbcTemplate db;
    private final Clock clock;
    private final OrderNotificationService notifications;

    public OrderNotificationJob(JdbcTemplate db, Clock clock, OrderNotificationService notifications) {
        this.db = db;
        this.clock = clock;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 5000)
    public void sendDueNotifications() {
        Instant now = clock.instant();
        if (now == null) {
            return;
        }
        send("status = 'PAID'", OrderNotificationType.PAYMENT_CONFIRMED, "PAYMENT_CONFIRMED");
        send("status = 'PAID' AND pickup_end > ? AND pickup_end <= ?", OrderNotificationType.PICKUP_REMINDER,
                "PICKUP_REMINDER", Timestamp.from(now), Timestamp.from(now.plusSeconds(REMINDER_SECONDS)));
        send("status = 'CANCELLED'", OrderNotificationType.ORDER_CANCELLED, "ORDER_CANCELLED");
        send("refund_status IN ('PENDING', 'COMPLETED', 'FAILED')", OrderNotificationType.REFUND_UPDATED,
                "REFUND_UPDATED");
    }

    private void send(String condition, OrderNotificationType type, String eventKey, Object... arguments) {
        String sql = """
                SELECT r.id, a.email, r.title, r.establishment_name, r.address, r.amount, r.quantity,
                       r.pickup_start, r.pickup_end, r.pickup_code, r.status, r.refund_status
                FROM reservation_orders r
                JOIN accounts a ON a.id = r.customer_id
                WHERE """ + " " + condition;
        List<OrderNotificationService.OrderNotification> orders = db.query(sql, (rs, row) ->
                new OrderNotificationService.OrderNotification(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("title"),
                        rs.getString("establishment_name"), rs.getString("address"), rs.getInt("amount"),
                        rs.getInt("quantity"), rs.getTimestamp("pickup_start").toInstant(),
                        rs.getTimestamp("pickup_end").toInstant(), rs.getString("pickup_code"),
                        rs.getString("status"), rs.getString("refund_status")), arguments);
        orders.forEach(order -> notifications.send(type,
                eventKey + (type == OrderNotificationType.REFUND_UPDATED ? ":" + order.refundStatus() : ""), order));
    }
}
