package com.example.taketoday.payment;

import com.example.taketoday.refund.RefundService;
import com.example.taketoday.reservation.ReservationService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Оплата брони (п. 4.6, 5.3.2 ТЗ, docs/reservations.md).
 *
 * <p>Порядок блокировок тот же, что у бронирования Жаныбека: сначала строка набора в offers, потом заказ и платёж.
 * Поэтому оплата, снятие просроченной брони и новая бронь того же набора не мешают друг другу.
 *
 * <ul>
 *     <li>сумму берём только из заказа на сервере — из браузера её не принимаем;</li>
 *     <li>ошибка оплаты бронь не снимает: пока идёт таймер, можно повторить;</li>
 *     <li>на одну оплату может прийти несколько сообщений — меняем данные только по первому;</li>
 *     <li>деньги после окончания брони: порции уже вернулись в продажу, заказ отменяем и помечаем к возврату.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public static final String UNAVAILABLE_MESSAGE =
            "Платёжная служба временно недоступна. Бронь сохранена — попробуйте ещё раз через минуту.";
    public static final String EXPIRED_MESSAGE =
            "Время на оплату истекло, бронь снята. Выберите набор в каталоге ещё раз.";

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final PaymentGateway gateway;
    private final PickupCodeGenerator codes;
    private final AuditLog audit;
    private final ReservationService reservations;
    private final RefundService refunds;

    public PaymentService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock,
                          PlatformTransactionManager manager, PaymentGateway gateway,
                          PickupCodeGenerator codes, AuditLog audit, ReservationService reservations,
                          RefundService refunds) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.gateway = gateway;
        this.codes = codes;
        this.audit = audit;
        this.reservations = reservations;
        this.refunds = refunds;
    }

    // ------------------------------------------------------------------ чтение

    /** Заказ покупателя. Заодно снимает его бронь, если время вышло (как страница брони Жаныбека). */
    public PaymentOrder order(UUID orderId, UUID customerId) {
        reservations.get(orderId, customerId); // проверка роли и владельца + снятие просроченных броней
        return db.query(ORDER_SQL + " WHERE r.id = ? AND r.customer_id = ?", ORDER_MAPPER, orderId, customerId)
                .stream().findFirst()
                .orElseThrow(() -> new PaymentException("Заказ не найден."));
    }

    /** Последняя попытка оплаты заказа — чтобы показать «оплата не прошла, попробуйте ещё раз». */
    public Optional<Payment> lastPayment(UUID orderId) {
        return db.query("SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC, id LIMIT 1",
                PAYMENT_MAPPER, orderId).stream().findFirst();
    }

    /** Последний успешный платёж (для «карта •••• 4242»), иначе последняя попытка. */
    public Optional<Payment> paymentForReceipt(UUID orderId) {
        return db.query("SELECT * FROM payments WHERE order_id = ? AND status = 'SUCCEEDED' ORDER BY created_at DESC, id LIMIT 1",
                PAYMENT_MAPPER, orderId).stream().findFirst().or(() -> lastPayment(orderId));
    }

    /** Платёж для страницы платёжной службы. Чужой платёж не показываем. */
    public Optional<Payment> paymentOf(String providerPaymentId, UUID customerId) {
        return db.query("""
                        SELECT p.* FROM payments p JOIN reservation_orders r ON r.id = p.order_id
                        WHERE p.provider_payment_id = ? AND r.customer_id = ?
                        """, PAYMENT_MAPPER, providerPaymentId, customerId).stream().findFirst();
    }

    // ------------------------------------------------------------------ начало оплаты

    /**
     * Покупатель нажал «Перейти к оплате».
     *
     * @return адрес страницы платёжной службы
     * @throws PaymentException понятная причина: бронь истекла, заказ уже оплачен, служба недоступна
     */
    public String start(UUID orderId, UUID customerId) {
        PaymentOrder order = order(orderId, customerId);
        requirePayable(order);

        UUID paymentId = UUID.randomUUID();
        // Внешний вызов — до блокировки набора, чтобы медленный банк не держал бронирование других покупателей.
        PaymentGateway.Checkout checkout = registerAtGateway(paymentId, order, customerId);

        transactions.executeWithoutResult(tx -> {
            lockOffer(order.offerId());
            PaymentOrder fresh = readOrder(orderId);
            Instant now = clock.instant();
            if (fresh.isAwaitingPayment() && !fresh.expiresAt().isAfter(now)) {
                releaseHold(fresh, now, "RESERVATION_EXPIRED");
            }
            requirePayable(readOrder(orderId));
            // Старые незавершённые попытки больше не действуют: оплатить можно только по последней.
            db.update("UPDATE payments SET status = 'CANCELLED', completed_at = ? WHERE order_id = ? AND status = 'CREATED'",
                    Timestamp.from(now), orderId);
            db.update("""
                            INSERT INTO payments (id, order_id, amount, status, provider_payment_id, created_at)
                            VALUES (?, ?, ?, 'CREATED', ?, ?)
                            """,
                    paymentId, orderId, fresh.amount(), checkout.providerPaymentId(), Timestamp.from(now));
            audit.log(AuditLog.PAYMENT_STARTED, orderId, paymentId, customerId,
                    "Начата оплата " + fresh.amount() + " сом");
        });
        return checkout.redirectUrl();
    }

    // ------------------------------------------------------------------ результат оплаты

    /**
     * Сообщение платёжной службы о результате (у настоящего банка — уведомление на сервер).
     * Можно вызывать сколько угодно раз для одного платежа: данные меняются только по первому сообщению.
     *
     * @param cardLast4 последние 4 цифры карты, больше от карты ничего не храним
     * @param error     причина отказа для покупателя (только при success = false)
     */
    public PaymentResult complete(String providerPaymentId, boolean success, String cardLast4, String error) {
        Payment found = db.query("SELECT * FROM payments WHERE provider_payment_id = ?", PAYMENT_MAPPER, providerPaymentId)
                .stream().findFirst()
                .orElseThrow(() -> new PaymentException("Платёж не найден."));
        UUID offerId = readOrder(found.orderId()).offerId();

        List<UUID> refundIds = new ArrayList<>(); // возвраты выполняем уже после транзакции (этап 5)
        PaymentResult result = transactions.execute(tx -> {
            lockOffer(offerId);
            Payment payment = db.query("SELECT * FROM payments WHERE id = ? FOR UPDATE", PAYMENT_MAPPER, found.id()).get(0);
            PaymentOrder order = readOrder(payment.orderId());
            Instant now = clock.instant();

            if (payment.status() == PaymentStatus.SUCCEEDED || payment.status() == PaymentStatus.FAILED) {
                audit.log(AuditLog.PAYMENT_DUPLICATE, order.id(), payment.id(), order.customerId(),
                        "Повторное сообщение об оплате (" + (success ? "успех" : "отказ")
                                + "), платёж уже " + payment.status() + " — ничего не меняли");
                return PaymentResult.DUPLICATE;
            }

            if (!success) {
                String reason = error == null || error.isBlank() ? "Оплата отклонена" : error;
                finishPayment(payment.id(), PaymentStatus.FAILED, cardLast4, reason, now);
                audit.log(AuditLog.PAYMENT_FAILED, order.id(), payment.id(), order.customerId(),
                        "Оплата не прошла: " + reason + ". Бронь сохранена до окончания таймера");
                return PaymentResult.FAILED;
            }

            if (payment.amount() != order.amount()) {
                finishPayment(payment.id(), PaymentStatus.FAILED, cardLast4, "Сумма платежа не совпадает с заказом", now);
                audit.log(AuditLog.ERROR, order.id(), payment.id(), order.customerId(),
                        "Сумма платежа " + payment.amount() + " сом не совпадает с суммой заказа " + order.amount() + " сом");
                return PaymentResult.FAILED;
            }

            finishPayment(payment.id(), PaymentStatus.SUCCEEDED, cardLast4, null, now);

            if (order.isPaid() || order.isIssued()) {
                db.update("UPDATE payments SET error_message = ? WHERE id = ?",
                        "Заказ уже был оплачен — эту сумму нужно вернуть", payment.id());
                audit.log(AuditLog.LATE_PAYMENT, order.id(), payment.id(), order.customerId(),
                        "Повторная оплата уже оплаченного заказа: " + payment.amount() + " сом нужно вернуть");
                refunds.request(order.id(), payment.id(), "AUTO", null, "Повторная оплата уже оплаченного заказа")
                        .ifPresent(refundIds::add);
                return PaymentResult.ALREADY_PAID;
            }

            if (order.canPay(now)) {
                markPaid(order, now);
                audit.log(AuditLog.PAYMENT_SUCCEEDED, order.id(), payment.id(), order.customerId(),
                        "Оплачено " + order.amount() + " сом, карта *" + cardLast4 + ". Выдан код получения");
                return PaymentResult.PAID;
            }

            // Деньги пришли, но бронь уже истекла (или заказ отменён). Порции к этому моменту могли купить другие —
            // поэтому заказ не восстанавливаем, а возвращаем деньги (docs/reservations.md).
            if (order.isAwaitingPayment()) {
                releaseHold(order, now, "LATE_PAYMENT");
            }
            // Причину «отменил покупатель/заведение» не затираем — меняем только «бронь истекла»
            db.update("""
                    UPDATE reservation_orders SET cancel_reason = 'LATE_PAYMENT'
                    WHERE id = ? AND (cancel_reason IS NULL OR cancel_reason IN ('RESERVATION_EXPIRED', 'LATE_PAYMENT'))
                    """, order.id());
            audit.log(AuditLog.LATE_PAYMENT, order.id(), payment.id(), order.customerId(),
                    "Оплата " + order.amount() + " сом пришла после окончания брони. Заказ отменён, деньги возвращаются");
            refunds.request(order.id(), payment.id(), "AUTO", null, "Оплата пришла после окончания брони")
                    .ifPresent(refundIds::add);
            return PaymentResult.LATE_PAYMENT;
        });
        refunds.processAll(refundIds);
        log.info("Payment {} finished: {}", providerPaymentId, result);
        return result;
    }

    // ------------------------------------------------------------------ внутреннее

    private PaymentGateway.Checkout registerAtGateway(UUID paymentId, PaymentOrder order, UUID customerId) {
        try {
            return gateway.start(paymentId, order.amount(), "TakeToday, заказ № " + order.getNumber());
        } catch (PaymentUnavailableException e) {
            audit.log(AuditLog.PAYMENT_UNAVAILABLE, order.id(), null, customerId,
                    "Не удалось начать оплату: платёжная служба недоступна (" + e.getMessage() + ")");
            throw new PaymentException(UNAVAILABLE_MESSAGE);
        }
    }

    private static void requirePayable(PaymentOrder order) {
        if (order.isPaid()) {
            throw new PaymentException("Этот заказ уже оплачен.");
        }
        if (!order.isAwaitingPayment()) {
            throw new PaymentException(EXPIRED_MESSAGE);
        }
    }

    /** AWAITING_PAYMENT → PAID: временная бронь становится продажей, появляется код получения. */
    private void markPaid(PaymentOrder order, Instant now) {
        int changed = db.update("""
                        UPDATE reservation_orders SET status = 'PAID', paid_at = ?, pickup_code = ?
                        WHERE id = ? AND status = 'AWAITING_PAYMENT'
                        """, Timestamp.from(now), codes.next(), order.id());
        if (changed != 1) {
            throw new IllegalStateException("Заказ " + order.id() + " изменился во время оплаты");
        }
        int counters = db.update("""
                        UPDATE offers SET reserved_portions = reserved_portions - ?, sold_portions = sold_portions + ?,
                            version = version + 1, updated_at = ?
                        WHERE id = ? AND reserved_portions >= ?
                        """, order.quantity(), order.quantity(), Timestamp.from(now), order.offerId(), order.quantity());
        if (counters != 1) {
            throw new IllegalStateException("Нарушен счётчик броней набора " + order.offerId());
        }
    }

    /** Снимает бронь одного заказа: порции возвращаются в продажу. */
    private void releaseHold(PaymentOrder order, Instant now, String reason) {
        int changed = db.update("""
                        UPDATE reservation_orders SET status = 'CANCELLED', cancel_reason = ?
                        WHERE id = ? AND status = 'AWAITING_PAYMENT'
                        """, reason, order.id());
        if (changed == 1) {
            int counters = db.update("""
                            UPDATE offers SET reserved_portions = reserved_portions - ?, version = version + 1, updated_at = ?
                            WHERE id = ? AND reserved_portions >= ?
                            """, order.quantity(), Timestamp.from(now), order.offerId(), order.quantity());
            if (counters != 1) {
                throw new IllegalStateException("Нарушен счётчик броней набора " + order.offerId());
            }
        }
    }

    private void finishPayment(UUID paymentId, PaymentStatus status, String last4, String error, Instant now) {
        db.update("UPDATE payments SET status = ?, card_last4 = ?, error_message = ?, completed_at = ? WHERE id = ?",
                status.name(), last4, error, Timestamp.from(now), paymentId);
    }

    private void lockOffer(UUID offerId) {
        db.queryForList("SELECT id FROM offers WHERE id = ? FOR UPDATE", offerId);
    }

    private PaymentOrder readOrder(UUID orderId) {
        return db.query(ORDER_SQL + " WHERE r.id = ?", ORDER_MAPPER, orderId).get(0);
    }

    private static final String ORDER_SQL = """
            SELECT r.*, o.regular_price, o.photo_path
            FROM reservation_orders r JOIN offers o ON o.id = r.offer_id
            """;

    private static final RowMapper<PaymentOrder> ORDER_MAPPER = (rs, n) -> new PaymentOrder(
            rs.getObject("id", UUID.class),
            rs.getObject("customer_id", UUID.class),
            rs.getObject("offer_id", UUID.class),
            rs.getString("title"),
            rs.getString("establishment_name"),
            rs.getString("address"),
            rs.getInt("amount"),
            rs.getInt("quantity"),
            rs.getInt("unit_price"),
            rs.getInt("regular_price"),
            rs.getString("photo_path"),
            instant(rs, "pickup_start"),
            instant(rs, "pickup_end"),
            rs.getString("status"),
            instant(rs, "expires_at"),
            rs.getString("pickup_code"),
            instant(rs, "paid_at"),
            rs.getString("cancel_reason"),
            rs.getString("refund_status"),
            instant(rs, "issued_at"),
            instant(rs, "cancelled_at"),
            rs.getString("cancel_comment"));

    private static final RowMapper<Payment> PAYMENT_MAPPER = (rs, n) -> new Payment(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getInt("amount"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("provider_payment_id"),
            rs.getString("card_last4"),
            rs.getString("error_message"),
            instant(rs, "created_at"),
            instant(rs, "completed_at"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
