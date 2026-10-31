package com.example.taketoday.refund;

import com.example.taketoday.payment.AuditLog;
import com.example.taketoday.payment.PaymentGateway;
import com.example.taketoday.payment.PaymentUnavailableException;
import com.example.taketoday.payment.RefundDeclinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Возврат денег (п. 4.6.4, 4.6.5, 4.10.3 ТЗ; SPEC, раздел 5.6).
 *
 * <p>Два шага:
 * <ol>
 *     <li>{@link #request} — внутри транзакции того, кто отменяет заказ: записать возврат PENDING.
 *         Если дальше что-то упадёт, запись останется, и {@link RefundJob} доведёт возврат до конца;</li>
 *     <li>{@link #process} — уже после транзакции: обратиться к платёжной службе и записать результат.</li>
 * </ol>
 *
 * <p>На один платёж не бывает двух возвратов PENDING/COMPLETED — повторный запрос возвращает уже существующий.
 * Деньги не уйдут дважды, даже если возврат попросят и покупатель, и администратор, и фоновая задача.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String REFUND_COMPLETED = "REFUND_COMPLETED";
    public static final String REFUND_FAILED = "REFUND_FAILED";

    /** Столько раз пробуем, если платёжная служба не отвечает; потом FAILED — дальше решает администратор. */
    static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final PaymentGateway gateway;
    private final AuditLog audit;

    public RefundService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock,
                         PlatformTransactionManager manager, PaymentGateway gateway, AuditLog audit) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.gateway = gateway;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ запись

    /**
     * Вернуть деньги по всем успешным платежам заказа, по которым ещё нет возврата.
     * Вызывать внутри транзакции, где строка заказа уже заблокирована.
     *
     * @return id возвратов, которые нужно передать в {@link #process} после транзакции
     */
    public List<UUID> requestForOrder(UUID orderId, String initiatedBy, UUID adminId, String reason) {
        List<UUID> ids = new ArrayList<>();
        List<UUID> paymentIds = db.query(
                "SELECT id FROM payments WHERE order_id = ? AND status = 'SUCCEEDED' ORDER BY created_at",
                (rs, n) -> rs.getObject(1, UUID.class), orderId);
        for (UUID paymentId : paymentIds) {
            request(orderId, paymentId, initiatedBy, adminId, reason).ifPresent(ids::add);
        }
        return ids;
    }

    /**
     * Вернуть деньги по одному платежу.
     *
     * @return id нового возврата; пусто, если по этому платежу возврат уже идёт или выполнен
     */
    public java.util.Optional<UUID> request(UUID orderId, UUID paymentId, String initiatedBy, UUID adminId, String reason) {
        Integer active = db.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_id = ? AND status IN ('PENDING', 'COMPLETED')",
                Integer.class, paymentId);
        if (active != null && active > 0) {
            return java.util.Optional.empty();
        }
        Integer amount = db.queryForObject("SELECT amount FROM payments WHERE id = ? AND status = 'SUCCEEDED'",
                Integer.class, paymentId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        db.update("""
                        INSERT INTO refunds (id, order_id, payment_id, amount, status, initiated_by, admin_id, reason, created_at)
                        VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                        """,
                id, orderId, paymentId, amount, initiatedBy, adminId, cut(reason), Timestamp.from(now));
        db.update("UPDATE reservation_orders SET refund_status = 'PENDING' WHERE id = ?", orderId);
        audit.log(REFUND_REQUESTED, orderId, paymentId, adminId,
                "Оформлен возврат " + amount + " сом (" + ("ADMIN".equals(initiatedBy) ? "администратор" : "автоматически")
                        + "): " + reason);
        return java.util.Optional.of(id);
    }

    // ------------------------------------------------------------------ выполнение

    /**
     * Обратиться к платёжной службе. Строка возврата заблокирована на время обращения — два обработчика
     * (сразу после отмены и фоновая задача) не вернут деньги дважды.
     */
    public RefundStatus process(UUID refundId) {
        RefundStatus result = transactions.execute(tx -> {
            Refund refund = db.query("SELECT * FROM refunds WHERE id = ? FOR UPDATE", MAPPER, refundId).stream()
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Возврат не найден: " + refundId));
            if (refund.status() != RefundStatus.PENDING) {
                return refund.status();
            }
            String providerPaymentId = db.queryForObject("SELECT provider_payment_id FROM payments WHERE id = ?",
                    String.class, refund.paymentId());
            Instant now = clock.instant();
            int attempt = refund.attempts() + 1;
            try {
                String providerRefundId = gateway.refund(providerPaymentId, refund.amount(), refund.reason());
                db.update("""
                                UPDATE refunds SET status = 'COMPLETED', provider_refund_id = ?, error_message = NULL,
                                    attempts = ?, completed_at = ?
                                WHERE id = ?
                                """, providerRefundId, attempt, Timestamp.from(now), refundId);
                audit.log(REFUND_COMPLETED, refund.orderId(), refund.paymentId(), refund.adminId(),
                        "Деньги возвращены: " + refund.amount() + " сом");
                updateOrder(refund.orderId());
                return RefundStatus.COMPLETED;
            } catch (PaymentUnavailableException e) {
                boolean giveUp = attempt >= MAX_ATTEMPTS;
                db.update("UPDATE refunds SET status = ?, error_message = ?, attempts = ?, completed_at = ? WHERE id = ?",
                        giveUp ? "FAILED" : "PENDING", "Платёжная служба недоступна", attempt,
                        giveUp ? Timestamp.from(now) : null, refundId);
                audit.log(giveUp ? REFUND_FAILED : AuditLog.PAYMENT_UNAVAILABLE, refund.orderId(), refund.paymentId(),
                        refund.adminId(), "Возврат " + refund.amount() + " сом: платёжная служба недоступна, попытка "
                                + attempt + " из " + MAX_ATTEMPTS + (giveUp ? ". Нужен ручной возврат" : ""));
                updateOrder(refund.orderId());
                return giveUp ? RefundStatus.FAILED : RefundStatus.PENDING;
            } catch (RefundDeclinedException e) {
                db.update("UPDATE refunds SET status = 'FAILED', error_message = ?, attempts = ?, completed_at = ? WHERE id = ?",
                        cut(e.getMessage()), attempt, Timestamp.from(now), refundId);
                audit.log(REFUND_FAILED, refund.orderId(), refund.paymentId(), refund.adminId(),
                        "Платёжная служба отказала в возврате: " + e.getMessage());
                updateOrder(refund.orderId());
                return RefundStatus.FAILED;
            }
        });
        log.info("Refund {} -> {}", refundId, result);
        return result;
    }

    public void processAll(List<UUID> refundIds) {
        for (UUID id : refundIds) {
            try {
                process(id);
            } catch (RuntimeException e) {
                // Запись PENDING осталась — фоновая задача повторит
                log.warn("Refund {} will be retried by the job: {}", id, e.getMessage());
            }
        }
    }

    /** Возвраты, которые ждут обращения к платёжной службе дольше {@code olderThanSeconds}. */
    List<UUID> pending(long olderThanSeconds) {
        return db.query("SELECT id FROM refunds WHERE status = 'PENDING' AND created_at < ? ORDER BY created_at LIMIT 50",
                (rs, n) -> rs.getObject(1, UUID.class),
                Timestamp.from(Instant.now().minusSeconds(olderThanSeconds)));
    }

    public List<Refund> forOrder(UUID orderId) {
        return db.query("SELECT * FROM refunds WHERE order_id = ? ORDER BY created_at", MAPPER, orderId);
    }

    /**
     * Состояние возврата заказа для покупателя. По каждому платежу: есть выполненный возврат — «возвращено»,
     * иначе идёт — «ожидает», иначе «не выполнен». Итог по заказу — худшее из них.
     * Неудачный возврат, который потом выполнили вручную, для покупателя уже «возвращено».
     */
    private void updateOrder(UUID orderId) {
        java.util.Map<UUID, Integer> best = new java.util.HashMap<>();
        db.query("SELECT payment_id, status FROM refunds WHERE order_id = ?", rs -> {
            int rank = switch (rs.getString(2)) {
                case "COMPLETED" -> 2;
                case "PENDING" -> 1;
                default -> 0;
            };
            best.merge(rs.getObject(1, UUID.class), rank, Math::max);
        }, orderId);
        int worst = best.values().stream().min(Integer::compare).orElse(-1);
        String summary = switch (worst) {
            case -1 -> "NONE";
            case 0 -> "FAILED";
            case 1 -> "PENDING";
            default -> "COMPLETED";
        };
        db.update("UPDATE reservation_orders SET refund_status = ? WHERE id = ?", summary, orderId);
    }

    private static String cut(String value) {
        if (value == null || value.isBlank()) return "Без комментария";
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    static final RowMapper<Refund> MAPPER = (rs, n) -> new Refund(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("payment_id", UUID.class),
            rs.getInt("amount"),
            RefundStatus.valueOf(rs.getString("status")),
            rs.getString("initiated_by"),
            rs.getObject("admin_id", UUID.class),
            rs.getString("reason"),
            rs.getString("provider_refund_id"),
            rs.getString("error_message"),
            rs.getInt("attempts"),
            instant(rs, "created_at"),
            instant(rs, "completed_at"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
