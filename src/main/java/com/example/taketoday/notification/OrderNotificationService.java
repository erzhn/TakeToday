package com.example.taketoday.notification;

import com.example.taketoday.common.AppTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderNotificationService {

    private final JdbcTemplate db;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String from;

    public OrderNotificationService(JdbcTemplate db, JavaMailSender mail, Clock clock,
                                    @Value("${app.mail-from}") String from) {
        this.db = db;
        this.mail = mail;
        this.clock = clock;
        this.from = from;
    }

    public void send(OrderNotificationType type, String eventKey, OrderNotification order) {
        if (!claim(type, eventKey, order.id())) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(order.email());
            message.setSubject(subject(type));
            message.setText(text(type, order));
            mail.send(message);
            markSent(eventKey, order.id());
        } catch (RuntimeException exception) {
            markFailed(eventKey, order.id(), exception.getMessage());
        }
    }

    private boolean claim(OrderNotificationType type, String eventKey, UUID orderId) {
        Instant now = clock.instant();
        int inserted = db.update("""
                INSERT INTO order_notifications(id, order_id, type, event_key, status, attempts, created_at)
                VALUES (?, ?, ?, ?, 'SENDING', 1, ?)
                ON CONFLICT (order_id, event_key) DO NOTHING
                """, UUID.randomUUID(), orderId, type.name(), eventKey, java.sql.Timestamp.from(now));
        if (inserted == 1) {
            return true;
        }
        return db.update("""
                UPDATE order_notifications
                SET status = 'SENDING', attempts = attempts + 1, last_error = NULL
                WHERE order_id = ? AND event_key = ? AND status = 'FAILED'
                """, orderId, eventKey) == 1;
    }

    private void markSent(String eventKey, UUID orderId) {
        db.update("""
                UPDATE order_notifications
                SET status = 'SENT', sent_at = ?, last_error = NULL
                WHERE order_id = ? AND event_key = ?
                """, java.sql.Timestamp.from(clock.instant()), orderId, eventKey);
    }

    private void markFailed(String eventKey, UUID orderId, String error) {
        String message = error == null || error.isBlank() ? "Неизвестная ошибка отправки" : error;
        db.update("""
                UPDATE order_notifications
                SET status = 'FAILED', last_error = ?
                WHERE order_id = ? AND event_key = ?
                """, message.substring(0, Math.min(message.length(), 500)), orderId, eventKey);
    }

    private static String subject(OrderNotificationType type) {
        return switch (type) {
            case PAYMENT_CONFIRMED -> "TakeToday — заказ оплачен";
            case PICKUP_REMINDER -> "TakeToday — скоро заканчивается время выдачи";
            case ORDER_CANCELLED -> "TakeToday — заказ отменён";
            case REFUND_UPDATED -> "TakeToday — изменился статус возврата";
        };
    }

    private static String text(OrderNotificationType type, OrderNotification order) {
        String common = "Заказ №" + order.number() + "\n"
                + "Набор: " + order.title() + "\n"
                + "Заведение: " + order.establishmentName() + "\n"
                + "Адрес: " + order.address() + "\n"
                + "Количество: " + order.quantity() + "\n"
                + "Стоимость: " + order.amount() + " сом\n"
                + "Получение: " + AppTime.format(order.pickupStart()) + " — " + AppTime.format(order.pickupEnd());
        return switch (type) {
            case PAYMENT_CONFIRMED -> common + "\n\nОплата подтверждена."
                    + "\nКод получения: " + order.codeGroups()
                    + "\nПокажите этот код сотруднику заведения при получении заказа.";
            case PICKUP_REMINDER -> common + "\n\nНапоминаем: время выдачи заказа скоро закончится.";
            case ORDER_CANCELLED -> common + "\n\nЗаказ отменён."
                    + (order.refundStatus().equals("NONE") ? "" : "\nСтатус возврата: " + refundText(order.refundStatus()) + ".");
            case REFUND_UPDATED -> common + "\n\nСтатус возврата денег: " + refundText(order.refundStatus()) + ".";
        };
    }

    private static String refundText(String status) {
        return switch (status) {
            case "PENDING" -> "ожидает обработки";
            case "COMPLETED" -> "выполнен";
            case "FAILED" -> "не выполнен, требуется обращение в поддержку";
            default -> "не требуется";
        };
    }

    public record OrderNotification(UUID id, String email, String title, String establishmentName,
                                    String address, int amount, int quantity, Instant pickupStart,
                                    Instant pickupEnd, String pickupCode, String status,
                                    String refundStatus) {
        public String number() { return id.toString().substring(0, 8).toUpperCase(); }

        public String codeGroups() {
            return pickupCode == null ? "не сформирован" : pickupCode.substring(0, 3) + " " + pickupCode.substring(3);
        }
    }
}
