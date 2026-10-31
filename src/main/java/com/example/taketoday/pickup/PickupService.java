package com.example.taketoday.pickup;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.payment.AuditLog;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Проверка кода и выдача заказа на кассе (п. 4.7.3–4.7.5, 5.3.2 ТЗ; SPEC, раздел 9).
 *
 * <p>Порядок проверок и тексты — по таблице из SPEC. Выдача — один UPDATE с условием status = 'PAID',
 * поэтому второе нажатие «Подтвердить выдачу» (или второй кассир) ничего не меняет и получает «Заказ уже выдан».
 */
@Service
public class PickupService {

    public static final String ORDER_ISSUED = "ORDER_ISSUED";
    public static final String PICKUP_CODE_WRONG = "PICKUP_CODE_WRONG";
    public static final String PICKUP_REFUSED = "PICKUP_REFUSED";

    /** Защита от подбора: столько неверных кодов за {@link #WRONG_WINDOW} — потом пауза. */
    static final int WRONG_LIMIT = 10;
    static final Duration WRONG_WINDOW = Duration.ofMinutes(10);

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AuditLog audit;

    public PickupService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock,
                         PlatformTransactionManager manager, AuditLog audit) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.audit = audit;
    }

    // ------------------------------------------------------------------ проверка кода

    /** «Проверить код»: ничего не меняет, только показывает заказ и можно ли его выдать. */
    public PickupCheck check(String raw, CurrentAccount staff) {
        if (tooManyWrongCodes(staff.getId())) {
            return PickupCheck.refused(null, "Слишком много неверных кодов подряд. Подождите 10 минут и попробуйте снова.");
        }
        Optional<PickupCodeInput> input = PickupCodeInput.parse(raw);
        if (input.isEmpty()) {
            return PickupCheck.refused(null, "Введите 6 цифр кода");
        }
        List<PickupOrder> found = find(input.get());
        Optional<PickupOrder> own = found.stream()
                .filter(o -> o.establishmentId().equals(staff.getEstablishmentId()))
                // Код уникален только среди оплаченных: у старых выданных заказов он мог повториться
                .min(Comparator.comparing((PickupOrder o) -> !"PAID".equals(o.status()))
                        .thenComparing(PickupOrder::paidAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (own.isEmpty()) {
            audit.log(PICKUP_CODE_WRONG, null, null, staff.getId(), found.isEmpty()
                    ? "Введён несуществующий код получения"
                    : "Введён код заказа другого заведения");
            return PickupCheck.refused(null, found.isEmpty() ? "Код не найден. Проверьте цифры" : "Этот заказ из другого заведения");
        }
        PickupOrder order = own.get();
        Instant now = clock.instant();
        String refusal = refusal(order, now);
        if (refusal != null) {
            audit.log(PICKUP_REFUSED, order.id(), null, staff.getId(), "Проверка кода: " + refusal);
            return PickupCheck.refused(order, refusal);
        }
        String warning = order.isEarly(now)
                ? "Окно выдачи начнётся в " + order.getStartText() + ". Выдать можно, если заказ уже готов."
                : null;
        return PickupCheck.ok(order, warning);
    }

    // ------------------------------------------------------------------ выдача

    /**
     * «Подтвердить выдачу». Все проверки повторяются: между проверкой и нажатием заказ могли выдать
     * на другой кассе или могло закончиться время выдачи.
     */
    public PickupOrder confirm(UUID orderId, CurrentAccount staff) {
        return transactions.execute(tx -> {
            // Блокируем строку заказа: два кассира с одним кодом не выдадут его дважды
            db.queryForList("SELECT id FROM reservation_orders WHERE id = ? FOR UPDATE", orderId);
            PickupOrder order = db.query(SELECT + " WHERE r.id = ?", MAPPER, orderId).stream().findFirst()
                    .filter(o -> o.establishmentId().equals(staff.getEstablishmentId()))
                    .orElseThrow(() -> new PickupException("Заказ не найден в вашем заведении"));
            Instant now = clock.instant();
            String refusal = refusal(order, now);
            if (refusal != null) {
                audit.log(PICKUP_REFUSED, order.id(), null, staff.getId(), "Подтверждение выдачи: " + refusal);
                throw new PickupException(refusal);
            }
            int changed = db.update("""
                            UPDATE reservation_orders SET status = 'ISSUED', issued_at = ?, issued_by = ?
                            WHERE id = ? AND status = 'PAID'
                            """, Timestamp.from(now), staff.getId(), orderId);
            if (changed != 1) {
                throw new PickupException("Заказ уже выдан");
            }
            audit.log(ORDER_ISSUED, orderId, null, staff.getId(),
                    "Заказ выдан: " + order.title() + ", " + order.quantity() + " порц.");
            return db.query(SELECT + " WHERE r.id = ?", MAPPER, orderId).get(0);
        });
    }

    // ------------------------------------------------------------------ цифры и очередь

    public PickupStats stats(UUID establishmentId) {
        if (establishmentId == null) return new PickupStats(0, 0, 0, 0);
        Instant now = clock.instant();
        LocalDate today = now.atZone(AppTime.ZONE).toLocalDate();
        Timestamp from = Timestamp.from(today.atStartOfDay(AppTime.ZONE).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(AppTime.ZONE).toInstant());
        String sql = "SELECT"
                + " COALESCE(SUM(CASE WHEN " + PAID_TODAY + " THEN 1 ELSE 0 END), 0) AS paid_count,"
                + " COALESCE(SUM(CASE WHEN " + PAID_TODAY + " THEN r.amount ELSE 0 END), 0) AS paid_amount,"
                + " COALESCE(SUM(CASE WHEN r.issued_at >= ? AND r.issued_at < ? THEN 1 ELSE 0 END), 0) AS issued_count,"
                + " COALESCE(SUM(CASE WHEN r.status = 'PAID' AND r.pickup_end > ? THEN 1 ELSE 0 END), 0) AS waiting"
                + " FROM reservation_orders r JOIN offers o ON o.id = r.offer_id"
                + " WHERE o.establishment_id = ?";
        return db.queryForObject(sql,
                (rs, n) -> new PickupStats(rs.getInt("paid_count"), rs.getLong("paid_amount"),
                        rs.getInt("issued_count"), rs.getInt("waiting")),
                from, to, from, to, from, to, from, to, from, to, Timestamp.from(now), establishmentId);
    }

    /**
     * «Оплачено сегодня»: заказ оплачен (не отменён) и либо оплата прошла сегодня, либо выдача сегодня —
     * заказ на сегодняшний набор, оплаченный накануне, тоже относится к сегодняшней выручке заведения.
     */
    private static final String PAID_TODAY = "r.status IN ('PAID', 'ISSUED', 'PICKUP_EXPIRED') AND "
            + "((r.paid_at >= ? AND r.paid_at < ?) OR (r.pickup_start >= ? AND r.pickup_start < ?))";

    /** Окно выдачи ближайшего заказа, который ждёт покупателя, — «19:00–22:00»; пусто, если никто не ждёт. */
    public java.util.Optional<String> nextWaitingWindow(UUID establishmentId) {
        return waiting(establishmentId, 1).stream().findFirst().map(o -> o.getPickupWindow().replace(" – ", "–"));
    }

    /** Оплаченные заказы, которые ещё ждут покупателя, — ближайшие по времени выдачи. */
    public List<PickupOrder> waiting(UUID establishmentId, int limit) {
        if (establishmentId == null) return List.of();
        return db.query(SELECT + """
                         WHERE o.establishment_id = ? AND r.status = 'PAID' AND r.pickup_end > ?
                        ORDER BY r.pickup_start, r.paid_at LIMIT ?
                        """, MAPPER, establishmentId, Timestamp.from(clock.instant()), limit);
    }

    /** Недавно выданные — чтобы кассир видел, что уже отдал. */
    public List<PickupOrder> recentlyIssued(UUID establishmentId, int limit) {
        if (establishmentId == null) return List.of();
        return db.query(SELECT + " WHERE o.establishment_id = ? AND r.status = 'ISSUED' ORDER BY r.issued_at DESC LIMIT ?",
                MAPPER, establishmentId, limit);
    }

    // ------------------------------------------------------------------ внутреннее

    /** Порядок как в SPEC, раздел 9. null — можно выдавать. */
    static String refusal(PickupOrder order, Instant now) {
        return switch (order.status()) {
            case "ISSUED" -> "Заказ уже выдан " + order.getIssuedAtText();
            case "CANCELLED" -> "Заказ отменен";
            case "PICKUP_EXPIRED" -> "Время получения истекло";
            case "PAID" -> now.isAfter(order.pickupEnd()) ? "Время получения истекло" : null;
            default -> "Заказ не оплачен";
        };
    }

    private List<PickupOrder> find(PickupCodeInput input) {
        return input.orderId() == null
                ? db.query(SELECT + " WHERE r.pickup_code = ?", MAPPER, input.code())
                : db.query(SELECT + " WHERE r.id = ? AND r.pickup_code = ?", MAPPER, input.orderId(), input.code());
    }

    private boolean tooManyWrongCodes(UUID staffId) {
        Integer wrong = db.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND event_type = ? AND created_at > ?",
                Integer.class, staffId, PICKUP_CODE_WRONG, Timestamp.from(Instant.now().minus(WRONG_WINDOW)));
        return wrong != null && wrong >= WRONG_LIMIT;
    }

    private static final String SELECT = """
            SELECT r.id, r.title, r.quantity, r.amount, r.pickup_start, r.pickup_end, r.status, r.paid_at, r.issued_at,
                   o.establishment_id, o.photo_path, a.name AS buyer_name, ib.name AS issued_by_name
            FROM reservation_orders r
            JOIN offers o ON o.id = r.offer_id
            JOIN accounts a ON a.id = r.customer_id
            LEFT JOIN accounts ib ON ib.id = r.issued_by
            """;

    private static final RowMapper<PickupOrder> MAPPER = (rs, n) -> new PickupOrder(
            rs.getObject("id", UUID.class),
            rs.getObject("establishment_id", UUID.class),
            rs.getString("title"),
            rs.getInt("quantity"),
            rs.getInt("amount"),
            rs.getString("buyer_name"),
            rs.getString("photo_path"),
            instant(rs, "pickup_start"),
            instant(rs, "pickup_end"),
            rs.getString("status"),
            instant(rs, "paid_at"),
            instant(rs, "issued_at"),
            rs.getString("issued_by_name"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
