package com.example.taketoday.refund;

import com.example.taketoday.payment.AuditLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.taketoday.common.AppTime.ZONE;

/**
 * Отмена заказа (п. 4.6.4 ТЗ; SPEC, раздел 6).
 *
 * <ul>
 *     <li>покупатель — неоплаченную бронь в любой момент, оплаченный заказ — только до начала выдачи;</li>
 *     <li>заведение — оплаченный заказ, который не может выдать;</li>
 *     <li>администратор — оплаченный заказ.</li>
 * </ul>
 * За оплаченный заказ деньги возвращаются автоматически ({@link RefundService}).
 *
 * <p>Блокировки в том же порядке, что у брони и оплаты: строка набора, потом заказ. Повторная отмена
 * ничего не меняет и не создаёт второй возврат.
 */
@Service
public class OrderCancelService {

    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final RefundService refunds;
    private final AuditLog audit;

    public OrderCancelService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock,
                              PlatformTransactionManager manager, RefundService refunds, AuditLog audit) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.refunds = refunds;
        this.audit = audit;
    }

    /** Строка заказа для отмены. */
    private record Target(UUID id, UUID offerId, UUID customerId, UUID establishmentId, int quantity,
                          String status, Instant pickupStart, Instant pickupEnd) {
    }

    /** Итог отмены: был ли заказ оплачен и что с возвратом. */
    public record Result(boolean wasPaid, RefundStatus refund) {
    }

    // ------------------------------------------------------------------ кто отменяет

    public Result cancelByCustomer(UUID orderId, UUID customerId) {
        return cancel(orderId, CancelReason.CUSTOMER, customerId, null, t -> {
            if (!t.customerId().equals(customerId)) throw new CancelException("Заказ не найден.");
            if ("PAID".equals(t.status()) && !clock.instant().isBefore(t.pickupStart())) {
                throw new CancelException("Отменить заказ можно только до начала выдачи ("
                        + t.pickupStart().atZone(ZONE).format(HH_MM) + "). Если не успеваете забрать — свяжитесь с заведением.");
            }
        });
    }

    public Result cancelByVenue(UUID orderId, UUID staffId, UUID establishmentId, String comment) {
        return cancel(orderId, CancelReason.VENUE, staffId, comment, t -> {
            if (!t.establishmentId().equals(establishmentId)) throw new CancelException("Заказ не найден в вашем заведении");
            if (!"PAID".equals(t.status())) throw new CancelException(notPaidText(t.status()));
        });
    }

    public Result cancelByAdmin(UUID orderId, UUID adminId, String comment) {
        return cancel(orderId, CancelReason.ADMIN, adminId, comment, t -> {
            if (!"PAID".equals(t.status())) throw new CancelException(notPaidText(t.status()));
        });
    }

    // ------------------------------------------------------------------ общая часть

    private interface Rule {
        void check(Target target);
    }

    private Result cancel(UUID orderId, CancelReason reason, UUID actorId, String comment, Rule rule) {
        UUID offerId = db.query("SELECT offer_id FROM reservation_orders WHERE id = ?",
                        (rs, n) -> rs.getObject(1, UUID.class), orderId).stream().findFirst()
                .orElseThrow(() -> new CancelException("Заказ не найден."));
        List<UUID> refundIds = new ArrayList<>();
        Result result = transactions.execute(tx -> {
            db.queryForList("SELECT id FROM offers WHERE id = ? FOR UPDATE", offerId);
            db.queryForList("SELECT id FROM reservation_orders WHERE id = ? FOR UPDATE", orderId);
            Target t = read(orderId);
            rule.check(t);
            Instant now = clock.instant();
            String text = comment == null || comment.isBlank() ? null
                    : comment.length() > 255 ? comment.substring(0, 255) : comment.trim();

            switch (t.status()) {
                case "AWAITING_PAYMENT" -> {
                    // Неоплаченная бронь: порции сразу возвращаются в продажу, страница оплаты больше не действует
                    update(t, reason, actorId, text, now, "AWAITING_PAYMENT");
                    counters("reserved_portions = reserved_portions - ?", t, now, "reserved_portions >= ?");
                    db.update("UPDATE payments SET status = 'CANCELLED', completed_at = ? WHERE order_id = ? AND status = 'CREATED'",
                            Timestamp.from(now), orderId);
                    audit.log(ORDER_CANCELLED, orderId, null, actorId, "Бронь отменена покупателем, порции вернулись в продажу");
                    return new Result(false, null);
                }
                case "PAID" -> {
                    update(t, reason, actorId, text, now, "PAID");
                    if (reason.returnsToSale()) {
                        counters("sold_portions = sold_portions - ?", t, now, "sold_portions >= ?");
                    } else {
                        // Заведение не может выдать: порции не возвращаем в продажу — уменьшаем и проданные, и всего
                        int changed = db.update("""
                                        UPDATE offers SET sold_portions = sold_portions - ?,
                                            total_portions = CASE WHEN total_portions - ? >= 1 THEN total_portions - ? ELSE total_portions END,
                                            status = CASE WHEN total_portions - ? >= 1 THEN status
                                                          WHEN status = 'ACTIVE' THEN 'WITHDRAWN' ELSE status END,
                                            version = version + 1, updated_at = ?
                                        WHERE id = ? AND sold_portions >= ?
                                        """, t.quantity(), t.quantity(), t.quantity(), t.quantity(),
                                Timestamp.from(now), t.offerId(), t.quantity());
                        if (changed != 1) throw new IllegalStateException("Нарушен счётчик продаж набора " + t.offerId());
                    }
                    db.update("UPDATE payments SET status = 'CANCELLED', completed_at = ? WHERE order_id = ? AND status = 'CREATED'",
                            Timestamp.from(now), orderId);
                    audit.log(ORDER_CANCELLED, orderId, null, actorId, switch (reason) {
                        case CUSTOMER -> "Покупатель отменил оплаченный заказ до начала выдачи";
                        case VENUE -> "Заведение не может выдать заказ" + (text == null ? "" : ": " + text);
                        case ADMIN -> "Заказ отменён администратором" + (text == null ? "" : ": " + text);
                    });
                    refundIds.addAll(refunds.requestForOrder(orderId, reason == CancelReason.ADMIN ? "ADMIN" : "AUTO",
                            reason == CancelReason.ADMIN ? actorId : null, refundReason(reason, text)));
                    return new Result(true, RefundStatus.PENDING);
                }
                case "CANCELLED" -> throw new CancelException("Заказ уже отменён.");
                case "ISSUED" -> throw new CancelException("Заказ уже выдан — отменить его нельзя.");
                default -> throw new CancelException("Время получения истекло — отменить заказ нельзя.");
            }
        });
        if (!result.wasPaid()) return result;
        refunds.processAll(refundIds);
        String status = db.queryForObject("SELECT refund_status FROM reservation_orders WHERE id = ?", String.class, orderId);
        return new Result(true, "NONE".equals(status) ? null : RefundStatus.valueOf(status));
    }

    private Target read(UUID orderId) {
        return db.query("""
                        SELECT r.id, r.offer_id, r.customer_id, o.establishment_id, r.quantity, r.status, r.pickup_start, r.pickup_end
                        FROM reservation_orders r JOIN offers o ON o.id = r.offer_id WHERE r.id = ?
                        """,
                (rs, n) -> new Target(rs.getObject("id", UUID.class), rs.getObject("offer_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getObject("establishment_id", UUID.class),
                        rs.getInt("quantity"), rs.getString("status"),
                        rs.getTimestamp("pickup_start").toInstant(), rs.getTimestamp("pickup_end").toInstant()),
                orderId).get(0);
    }

    private void update(Target t, CancelReason reason, UUID actorId, String comment, Instant now, String fromStatus) {
        int changed = db.update("""
                        UPDATE reservation_orders SET status = 'CANCELLED', cancel_reason = ?, cancelled_at = ?,
                            cancelled_by = ?, cancel_comment = ?
                        WHERE id = ? AND status = ?
                        """, reason.name(), Timestamp.from(now), actorId, comment, t.id(), fromStatus);
        if (changed != 1) throw new CancelException("Заказ изменился. Обновите страницу.");
    }

    private void counters(String set, Target t, Instant now, String guard) {
        int changed = db.update("UPDATE offers SET " + set + ", version = version + 1, updated_at = ? WHERE id = ? AND " + guard,
                t.quantity(), Timestamp.from(now), t.offerId(), t.quantity());
        if (changed != 1) throw new IllegalStateException("Нарушен счётчик порций набора " + t.offerId());
    }

    private static String refundReason(CancelReason reason, String comment) {
        String base = switch (reason) {
            case CUSTOMER -> "Покупатель отменил заказ до начала выдачи";
            case VENUE -> "Заведение не может выдать заказ";
            case ADMIN -> "Отмена администратором";
        };
        return comment == null ? base : base + ": " + comment;
    }

    private static String notPaidText(String status) {
        return switch (status) {
            case "CANCELLED" -> "Заказ уже отменён.";
            case "ISSUED" -> "Заказ уже выдан — отменить его нельзя.";
            case "AWAITING_PAYMENT" -> "Заказ ещё не оплачен.";
            default -> "Время получения истекло — отменить заказ нельзя.";
        };
    }
}
