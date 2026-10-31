package com.example.taketoday.admin;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.payment.AuditLog;
import com.example.taketoday.refund.OrderCancelService;
import com.example.taketoday.refund.RefundService;
import com.example.taketoday.refund.RefundStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Админ-панель (п. 4.10.1–4.10.3 ТЗ): просмотр пользователей, заведений, предложений, платежей и возвратов;
 * блокировка учётных записей, скрытие предложений, ручной возврат денег.
 * Каждое действие администратора пишется в журнал с причиной (п. 5.3.4).
 */
@Service
public class AdminService {

    public static final String USER_BLOCKED = "USER_BLOCKED";
    public static final String USER_UNBLOCKED = "USER_UNBLOCKED";
    public static final String OFFER_HIDDEN = "OFFER_HIDDEN";
    public static final String OFFER_UNHIDDEN = "OFFER_UNHIDDEN";

    static final int LIMIT = 200;

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AuditLog audit;
    private final RefundService refunds;
    private final OrderCancelService cancels;

    public AdminService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock, PlatformTransactionManager manager,
                        AuditLog audit, RefundService refunds, OrderCancelService cancels) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.audit = audit;
        this.refunds = refunds;
        this.cancels = cancels;
    }

    // ------------------------------------------------------------------ обзор

    public AdminRows.Overview overview() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(AppTime.ZONE).toLocalDate();
        Timestamp from = Timestamp.from(today.atStartOfDay(AppTime.ZONE).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(AppTime.ZONE).toInstant());
        return new AdminRows.Overview(
                count("SELECT COUNT(*) FROM accounts"),
                count("SELECT COUNT(*) FROM accounts WHERE role = 'CUSTOMER'"),
                count("SELECT COUNT(*) FROM accounts WHERE role = 'STAFF'"),
                count("SELECT COUNT(*) FROM accounts WHERE blocked = TRUE"),
                count("SELECT COUNT(*) FROM establishments"),
                count("SELECT COUNT(*) FROM offers WHERE status = 'ACTIVE' AND pickup_end > ?", Timestamp.from(now)),
                count("SELECT COUNT(*) FROM reservation_orders WHERE created_at >= ? AND created_at < ?", from, to),
                count("SELECT COUNT(*) FROM reservation_orders WHERE paid_at >= ? AND paid_at < ?", from, to),
                count("SELECT COALESCE(SUM(amount), 0) FROM reservation_orders WHERE paid_at >= ? AND paid_at < ? AND status <> 'CANCELLED'", from, to),
                count("SELECT COUNT(*) FROM reservation_orders WHERE issued_at >= ? AND issued_at < ?", from, to),
                count("SELECT COUNT(*) FROM refunds WHERE status = 'PENDING'"),
                count("SELECT COUNT(*) FROM refunds WHERE status = 'FAILED' AND NOT EXISTS (SELECT 1 FROM refunds s "
                        + "WHERE s.payment_id = refunds.payment_id AND s.status IN ('COMPLETED', 'PENDING'))"),
                count("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?", from, to));
    }

    // ------------------------------------------------------------------ пользователи

    public List<AdminRows.User> users(String q, String role) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.*, e.name AS establishment_name,
                       (SELECT COUNT(*) FROM reservation_orders r WHERE r.customer_id = a.id) AS orders
                FROM accounts a LEFT JOIN establishments e ON e.owner_id = a.id WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        like(sql, args, q, "LOWER(a.name)", "LOWER(a.email)", "LOWER(COALESCE(a.phone, ''))", "LOWER(COALESCE(e.name, ''))");
        if (role != null && List.of("CUSTOMER", "STAFF", "ADMIN").contains(role)) {
            sql.append(" AND a.role = ?");
            args.add(role);
        }
        if ("BLOCKED".equals(role)) sql.append(" AND a.blocked = TRUE");
        sql.append(" ORDER BY a.created_at DESC LIMIT ").append(LIMIT);
        return db.query(sql.toString(), (rs, n) -> new AdminRows.User(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"), rs.getString("phone"),
                rs.getString("role"), rs.getBoolean("verified"), rs.getBoolean("blocked"), rs.getString("blocked_reason"),
                instant(rs, "blocked_at"), instant(rs, "created_at"), rs.getString("establishment_name"),
                rs.getLong("orders")), args.toArray());
    }

    /**
     * Блокировка нарушителя. Сессии сразу перестают действовать (auth_version + 1 — так работает SessionGuard
     * Жаныбека), предложения заблокированного заведения пропадают из каталога, войти нельзя.
     */
    public void block(UUID userId, UUID adminId, String reason) {
        String why = required(reason, "Укажите причину блокировки — её увидят в журнале.");
        if (userId.equals(adminId)) throw new AdminException("Нельзя заблокировать самого себя.");
        String role = roleOf(userId);
        if ("ADMIN".equals(role)) throw new AdminException("Администратора заблокировать нельзя.");
        int changed = db.update("""
                        UPDATE accounts SET blocked = TRUE, blocked_at = ?, blocked_reason = ?, auth_version = auth_version + 1
                        WHERE id = ? AND blocked = FALSE
                        """, Timestamp.from(clock.instant()), why, userId);
        if (changed == 0) throw new AdminException("Учётная запись уже заблокирована.");
        audit.log(USER_BLOCKED, null, null, adminId, "Заблокирован " + emailOf(userId) + ": " + why);
    }

    public void unblock(UUID userId, UUID adminId) {
        roleOf(userId);
        int changed = db.update("""
                        UPDATE accounts SET blocked = FALSE, blocked_at = NULL, blocked_reason = NULL
                        WHERE id = ? AND blocked = TRUE
                        """, userId);
        if (changed == 0) throw new AdminException("Учётная запись не заблокирована.");
        audit.log(USER_UNBLOCKED, null, null, adminId, "Разблокирован " + emailOf(userId));
    }

    // ------------------------------------------------------------------ заведения

    public List<AdminRows.Venue> venues(String q) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.name, e.address, e.category, e.contact, a.id AS owner_id, a.name AS owner_name,
                       a.email AS owner_email, a.blocked AS owner_blocked,
                       (SELECT COUNT(*) FROM offers o WHERE o.establishment_id = e.id AND o.status = 'ACTIVE' AND o.pickup_end > ?) AS active_offers,
                       (SELECT COUNT(*) FROM offers o WHERE o.establishment_id = e.id) AS total_offers,
                       (SELECT COUNT(*) FROM reservation_orders r JOIN offers o ON o.id = r.offer_id
                         WHERE o.establishment_id = e.id AND r.status IN ('PAID', 'ISSUED', 'PICKUP_EXPIRED')) AS paid_orders,
                       (SELECT COALESCE(SUM(r.amount), 0) FROM reservation_orders r JOIN offers o ON o.id = r.offer_id
                         WHERE o.establishment_id = e.id AND r.status IN ('PAID', 'ISSUED', 'PICKUP_EXPIRED')) AS revenue
                FROM establishments e JOIN accounts a ON a.id = e.owner_id WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(clock.instant()));
        like(sql, args, q, "LOWER(e.name)", "LOWER(e.address)", "LOWER(a.email)");
        sql.append(" ORDER BY e.name LIMIT ").append(LIMIT);
        return db.query(sql.toString(), (rs, n) -> new AdminRows.Venue(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address"), rs.getString("category"),
                rs.getString("contact"), rs.getString("owner_name"), rs.getString("owner_email"),
                rs.getObject("owner_id", UUID.class), rs.getBoolean("owner_blocked"), rs.getLong("active_offers"),
                rs.getLong("total_offers"), rs.getLong("paid_orders"), rs.getLong("revenue")), args.toArray());
    }

    // ------------------------------------------------------------------ предложения

    public List<AdminRows.Offer> offers(String q, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.*, e.name AS venue_name, a.blocked AS owner_blocked
                FROM offers o JOIN establishments e ON e.id = o.establishment_id JOIN accounts a ON a.id = e.owner_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        like(sql, args, q, "LOWER(o.title)", "LOWER(e.name)");
        if ("HIDDEN".equals(status) || "WITHDRAWN".equals(status)) {
            sql.append(" AND o.status = ?");
            args.add(status);
        } else if ("ACTIVE".equals(status)) {
            sql.append(" AND o.status = 'ACTIVE' AND o.pickup_end > ?");
            args.add(Timestamp.from(clock.instant()));
        }
        sql.append(" ORDER BY o.pickup_start DESC LIMIT ").append(LIMIT);
        return db.query(sql.toString(), (rs, n) -> new AdminRows.Offer(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("venue_name"),
                rs.getInt("regular_price"), rs.getInt("discount_price"), rs.getInt("total_portions"),
                rs.getInt("reserved_portions"), rs.getInt("sold_portions"), instant(rs, "pickup_start"),
                instant(rs, "pickup_end"), rs.getString("status"), rs.getString("hidden_reason"),
                rs.getBoolean("owner_blocked"), rs.getString("photo_path")), args.toArray());
    }

    /**
     * Скрыть предложение: пропадает из каталога и со страницы набора, новые брони невозможны.
     * Уже оплаченные заказы остаются в силе — их можно выдать или отменить с возвратом.
     */
    public void hideOffer(UUID offerId, UUID adminId, String reason) {
        String why = required(reason, "Укажите причину — её увидит заведение.");
        int changed = db.update("""
                        UPDATE offers SET status = 'HIDDEN', hidden_reason = ?, version = version + 1, updated_at = ?
                        WHERE id = ? AND status <> 'HIDDEN'
                        """, why, Timestamp.from(clock.instant()), offerId);
        if (changed == 0) throw new AdminException("Предложение не найдено или уже скрыто.");
        audit.log(OFFER_HIDDEN, null, null, adminId, "Скрыто предложение «" + titleOf(offerId) + "»: " + why);
    }

    /** Вернуть скрытое: становится «снято заведением» — выставить снова решает само заведение. */
    public void unhideOffer(UUID offerId, UUID adminId) {
        int changed = db.update("""
                        UPDATE offers SET status = 'WITHDRAWN', hidden_reason = NULL, version = version + 1, updated_at = ?
                        WHERE id = ? AND status = 'HIDDEN'
                        """, Timestamp.from(clock.instant()), offerId);
        if (changed == 0) throw new AdminException("Предложение не скрыто.");
        audit.log(OFFER_UNHIDDEN, null, null, adminId, "Снято скрытие с предложения «" + titleOf(offerId) + "»");
    }

    // ------------------------------------------------------------------ платежи и возвраты

    public List<AdminRows.Payment> payments(String q, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.order_id, p.created_at, p.amount, p.status, p.card_last4, p.error_message,
                       r.status AS order_status, r.refund_status, r.title, r.establishment_name,
                       a.name AS buyer_name, a.email AS buyer_email
                FROM payments p JOIN reservation_orders r ON r.id = p.order_id JOIN accounts a ON a.id = r.customer_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        likeOrder(sql, args, q);
        if (status != null && List.of("SUCCEEDED", "FAILED", "CREATED", "CANCELLED").contains(status)) {
            sql.append(" AND p.status = ?");
            args.add(status);
        }
        if ("REFUND".equals(status)) sql.append(" AND r.refund_status <> 'NONE'");
        sql.append(" ORDER BY p.created_at DESC LIMIT ").append(LIMIT);
        return db.query(sql.toString(), PAYMENT_ROW, args.toArray());
    }

    public List<AdminRows.Refund> refunds(String q, String status) {
        StringBuilder sql = new StringBuilder(REFUND_SQL + " WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        likeOrder(sql, args, q);
        if (status != null && List.of("PENDING", "COMPLETED", "FAILED").contains(status)) {
            sql.append(" AND f.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY f.created_at DESC LIMIT ").append(LIMIT);
        return db.query(sql.toString(), REFUND_MAPPER, args.toArray());
    }

    /** Заказы, где возврат не удался и не выполнен позже, — им нужен ручной возврат. */
    public List<AdminRows.Refund> needManualRefund() {
        return db.query(REFUND_SQL + """
                 WHERE f.status = 'FAILED' AND NOT EXISTS (SELECT 1 FROM refunds s
                   WHERE s.payment_id = f.payment_id AND s.status IN ('COMPLETED', 'PENDING'))
                 ORDER BY f.created_at
                """, REFUND_MAPPER);
    }

    public Optional<AdminRows.Order> order(UUID orderId) {
        return db.query("""
                        SELECT r.*, a.name AS buyer_name, a.email AS buyer_email
                        FROM reservation_orders r JOIN accounts a ON a.id = r.customer_id WHERE r.id = ?
                        """, (rs, n) -> new AdminRows.Order(
                        rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("establishment_name"),
                        rs.getString("address"), rs.getInt("amount"), rs.getInt("quantity"), rs.getString("status"),
                        rs.getString("cancel_reason"), rs.getString("cancel_comment"), rs.getString("refund_status"),
                        rs.getString("buyer_name"), rs.getString("buyer_email"), instant(rs, "created_at"),
                        instant(rs, "paid_at"), instant(rs, "issued_at"), instant(rs, "cancelled_at"),
                        instant(rs, "pickup_start"), instant(rs, "pickup_end")),
                orderId).stream().findFirst();
    }

    public List<AdminRows.Payment> paymentsOf(UUID orderId) {
        return db.query(PAYMENT_BY_ORDER, PAYMENT_ROW, orderId);
    }

    public List<AdminRows.Refund> refundsOf(UUID orderId) {
        return db.query(REFUND_SQL + " WHERE f.order_id = ? ORDER BY f.created_at", REFUND_MAPPER, orderId);
    }

    /** Есть ли успешный платёж, по которому деньги ещё не вернули и возврат не идёт. */
    public boolean canRefund(UUID orderId) {
        Integer open = db.queryForObject("""
                SELECT COUNT(*) FROM payments p WHERE p.order_id = ? AND p.status = 'SUCCEEDED'
                  AND NOT EXISTS (SELECT 1 FROM refunds f WHERE f.payment_id = p.id AND f.status IN ('PENDING', 'COMPLETED'))
                """, Integer.class, orderId);
        return open != null && open > 0;
    }

    public boolean hasPendingRefund(UUID orderId) {
        Integer pending = db.queryForObject("SELECT COUNT(*) FROM refunds WHERE order_id = ? AND status = 'PENDING'",
                Integer.class, orderId);
        return pending != null && pending > 0;
    }

    /**
     * Ручной возврат (п. 4.10.3). Оплаченный и ещё не выданный заказ сначала отменяется — иначе по коду его
     * могли бы выдать уже после возврата денег. По выданному заказу (жалоба) возврат оформляется без отмены.
     */
    public RefundStatus manualRefund(UUID orderId, UUID adminId, String reason) {
        String why = required(reason, "Укажите причину возврата — её увидят в журнале.");
        AdminRows.Order order = order(orderId).orElseThrow(() -> new AdminException("Заказ не найден."));
        if (order.isPaid()) {
            return cancels.cancelByAdmin(orderId, adminId, why).refund();
        }
        List<UUID> created = transactions.execute(tx -> {
            db.queryForList("SELECT id FROM reservation_orders WHERE id = ? FOR UPDATE", orderId);
            return refunds.requestForOrder(orderId, "ADMIN", adminId, why);
        });
        if (created.isEmpty()) {
            throw new AdminException(hasPendingRefund(orderId)
                    ? "Возврат по этому заказу уже в обработке — нажмите «Повторить сейчас»."
                    : "По этому заказу нечего возвращать: успешных платежей без возврата нет.");
        }
        refunds.processAll(created);
        return RefundStatus.valueOf(db.queryForObject("SELECT refund_status FROM reservation_orders WHERE id = ?",
                String.class, orderId));
    }

    /** Повторить зависший возврат PENDING (например, служба была недоступна) прямо сейчас. */
    public RefundStatus retryPending(UUID orderId) {
        List<UUID> pending = db.query("SELECT id FROM refunds WHERE order_id = ? AND status = 'PENDING'",
                (rs, n) -> rs.getObject(1, UUID.class), orderId);
        if (pending.isEmpty()) throw new AdminException("Нет возвратов в обработке.");
        refunds.processAll(pending);
        return RefundStatus.valueOf(db.queryForObject("SELECT refund_status FROM reservation_orders WHERE id = ?",
                String.class, orderId));
    }

    // ------------------------------------------------------------------ журнал

    public List<AdminRows.Event> events(String q, UUID orderId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.created_at, l.event_type, l.order_id, a.name AS user_name, l.details
                FROM audit_log l LEFT JOIN accounts a ON a.id = l.user_id WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (orderId != null) {
            sql.append(" AND l.order_id = ?");
            args.add(orderId);
        }
        if (q != null && !q.isBlank()) {
            String value = q.trim().toLowerCase(Locale.ROOT);
            sql.append(" AND (LOWER(l.event_type) LIKE ? ESCAPE '!' OR LOWER(l.details) LIKE ? ESCAPE '!'"
                    + " OR LOWER(CAST(l.order_id AS VARCHAR(36))) LIKE ? ESCAPE '!')");
            args.add("%" + escape(value) + "%");
            args.add("%" + escape(value) + "%");
            args.add(escape(value) + "%");
        }
        sql.append(" ORDER BY l.created_at DESC LIMIT ").append(Math.min(limit, LIMIT));
        return db.query(sql.toString(), (rs, n) -> new AdminRows.Event(instant(rs, "created_at"),
                rs.getString("event_type"), rs.getObject("order_id", UUID.class), rs.getString("user_name"),
                rs.getString("details")), args.toArray());
    }

    // ------------------------------------------------------------------ внутреннее

    private static final String REFUND_SQL = """
            SELECT f.*, ad.name AS admin_name, a.name AS buyer_name, r.title
            FROM refunds f JOIN reservation_orders r ON r.id = f.order_id JOIN accounts a ON a.id = r.customer_id
            LEFT JOIN accounts ad ON ad.id = f.admin_id
            """;

    private static final String PAYMENT_BY_ORDER = """
            SELECT p.id, p.order_id, p.created_at, p.amount, p.status, p.card_last4, p.error_message,
                   r.status AS order_status, r.refund_status, r.title, r.establishment_name,
                   a.name AS buyer_name, a.email AS buyer_email
            FROM payments p JOIN reservation_orders r ON r.id = p.order_id JOIN accounts a ON a.id = r.customer_id
            WHERE p.order_id = ? ORDER BY p.created_at
            """;

    private static final RowMapper<AdminRows.Payment> PAYMENT_ROW = (rs, n) -> new AdminRows.Payment(
            rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), instant(rs, "created_at"),
            rs.getInt("amount"), rs.getString("status"), rs.getString("card_last4"), rs.getString("error_message"),
            rs.getString("order_status"), rs.getString("refund_status"), rs.getString("buyer_name"),
            rs.getString("buyer_email"), rs.getString("establishment_name"), rs.getString("title"));

    private static final RowMapper<AdminRows.Refund> REFUND_MAPPER = (rs, n) -> new AdminRows.Refund(
            rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getInt("amount"),
            rs.getString("status"), rs.getString("initiated_by"), rs.getString("admin_name"), rs.getString("reason"),
            rs.getString("error_message"), rs.getInt("attempts"), instant(rs, "created_at"), instant(rs, "completed_at"),
            rs.getString("buyer_name"), rs.getString("title"));

    /** Поиск по номеру заказа (первые символы UUID), имени и почте покупателя, заведению. */
    private static void likeOrder(StringBuilder sql, List<Object> args, String q) {
        if (q == null || q.isBlank()) return;
        String value = escape(q.trim().toLowerCase(Locale.ROOT).replace("№", "").replace("#", "").trim());
        sql.append(" AND (LOWER(CAST(r.id AS VARCHAR(36))) LIKE ? ESCAPE '!' OR LOWER(a.name) LIKE ? ESCAPE '!'"
                + " OR LOWER(a.email) LIKE ? ESCAPE '!' OR LOWER(r.establishment_name) LIKE ? ESCAPE '!')");
        args.add(value + "%");
        args.add("%" + value + "%");
        args.add("%" + value + "%");
        args.add("%" + value + "%");
    }

    private static void like(StringBuilder sql, List<Object> args, String q, String... columns) {
        if (q == null || q.isBlank()) return;
        String value = "%" + escape(q.trim().toLowerCase(Locale.ROOT)) + "%";
        sql.append(" AND (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append(columns[i]).append(" LIKE ? ESCAPE '!'");
            args.add(value);
        }
        sql.append(")");
    }

    private static String escape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new AdminException(message);
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String roleOf(UUID userId) {
        return db.query("SELECT role FROM accounts WHERE id = ?", (rs, n) -> rs.getString(1), userId).stream()
                .findFirst().orElseThrow(() -> new AdminException("Учётная запись не найдена."));
    }

    private String emailOf(UUID userId) {
        return db.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, userId);
    }

    private String titleOf(UUID offerId) {
        return db.queryForObject("SELECT title FROM offers WHERE id = ?", String.class, offerId);
    }

    private long count(String sql, Object... args) {
        Long value = db.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
