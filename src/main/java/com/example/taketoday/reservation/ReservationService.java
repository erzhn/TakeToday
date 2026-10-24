package com.example.taketoday.reservation;

import com.example.taketoday.auth.ApiError;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    public static final Duration HOLD_TIME = Duration.ofMinutes(3);
    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private record CheckoutOffer(String title, String establishmentName, String address, int price,
                                 Instant pickupStart, Instant pickupEnd, String status, boolean ownerBlocked) {}
    private static final RowMapper<Reservation> MAPPER = (rs, n) -> new Reservation(
            rs.getObject("id", UUID.class), rs.getObject("offer_id", UUID.class), rs.getString("title"),
            rs.getString("establishment_name"), rs.getString("address"), rs.getInt("amount"), rs.getInt("quantity"), rs.getInt("unit_price"),
            rs.getTimestamp("pickup_start").toInstant(), rs.getTimestamp("pickup_end").toInstant(),
            rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant());

    public ReservationService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock,
                              PlatformTransactionManager manager) {
        this.db = db;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    public Reservation create(UUID customerId, UUID offerId, UUID requestKey) {
        return create(customerId, offerId, requestKey, 1);
    }

    public Reservation create(UUID customerId, UUID offerId, UUID requestKey, int quantity) {
        if (quantity < 1) throw new ApiError(HttpStatus.BAD_REQUEST, "Укажите целое количество порций от 1.");
        return transactions.execute(tx -> {
            requireCustomer(customerId);
            // Блокировка общей строки offers сериализует бронирование, освобождение и будущую оплату.
            lockOffer(offerId);
            Instant now = clock.instant(); // Срок отсчитывается после ожидания блокировки.
            releaseExpired(offerId, now);
            var previous = db.query("SELECT * FROM reservation_orders WHERE customer_id=? AND request_key=?",
                    MAPPER, customerId, requestKey);
            if (!previous.isEmpty()) {
                if (!previous.get(0).offerId().equals(offerId) || previous.get(0).quantity() != quantity)
                    throw new ApiError(HttpStatus.CONFLICT, "Этот запрос уже использован с другим набором или количеством порций. Обновите страницу.");
                return previous.get(0);
            }
            var rows = db.query("""
                    SELECT o.*, e.name AS establishment_name, e.address, a.blocked AS owner_blocked
                    FROM offers o JOIN establishments e ON e.id=o.establishment_id
                    JOIN accounts a ON a.id=e.owner_id WHERE o.id=?
                    """, (rs, n) -> new CheckoutOffer(rs.getString("title"), rs.getString("establishment_name"),
                    rs.getString("address"), rs.getInt("discount_price"), rs.getTimestamp("pickup_start").toInstant(),
                    rs.getTimestamp("pickup_end").toInstant(), rs.getString("status"), rs.getBoolean("owner_blocked")), offerId);
            var offer = rows.get(0);
            if (offer.ownerBlocked() || !"ACTIVE".equals(offer.status()))
                throw new ApiError(HttpStatus.CONFLICT, "Предложение снято с продажи. Выберите другой набор.");
            if (!offer.pickupEnd().isAfter(now))
                throw new ApiError(HttpStatus.CONFLICT, "Время выдачи завершилось. Выберите другой набор.");
            long total = (long) offer.price() * quantity;
            if (total > Integer.MAX_VALUE)
                throw new ApiError(HttpStatus.BAD_REQUEST, "Сумма заказа слишком велика. Уменьшите количество порций.");
            int changed = db.update("""
                    UPDATE offers SET reserved_portions=reserved_portions+?, version=version+1, updated_at=?
                    WHERE id=? AND total_portions-reserved_portions-sold_portions >= ?
                    """, quantity, Timestamp.from(now), offerId, quantity);
            if (changed != 1)
                throw new ApiError(HttpStatus.CONFLICT, "Недостаточно свободных порций. Уменьшите количество или выберите другой набор.");
            UUID id = UUID.randomUUID();
            db.update("""
                    INSERT INTO reservation_orders(id,customer_id,offer_id,request_key,title,establishment_name,address,
                        amount,quantity,unit_price,pickup_start,pickup_end,status,created_at,expires_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'AWAITING_PAYMENT',?,?)
                    """, id, customerId, offerId, requestKey, offer.title(), offer.establishmentName(),
                    offer.address(), (int) total, quantity, offer.price(), Timestamp.from(offer.pickupStart()), Timestamp.from(offer.pickupEnd()),
                    Timestamp.from(now), Timestamp.from(now.plus(HOLD_TIME)));
            afterCommit(() -> log.info("Created reservation {} for offer {}, expires at {}", id, offerId, now.plus(HOLD_TIME)));
            return readOwned(id, customerId);
        });
    }

    public Reservation get(UUID id, UUID customerId) {
        return transactions.execute(tx -> {
            requireCustomer(customerId);
            Reservation order = readOwned(id, customerId);
            lockOffer(order.offerId());
            releaseExpired(order.offerId(), clock.instant());
            return readOwned(id, customerId);
        });
    }

    /** Каждый набор обрабатывается отдельной транзакцией: сбой не оставляет половину операции. */
    public void expireDue() {
        List<UUID> offers = db.query("""
                SELECT DISTINCT offer_id FROM reservation_orders
                WHERE status='AWAITING_PAYMENT' AND expires_at<=? ORDER BY offer_id
                """, (rs, n) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
        for (UUID offerId : offers) {
            transactions.executeWithoutResult(tx -> {
                lockOffer(offerId);
                releaseExpired(offerId, clock.instant());
            });
        }
    }

    private void releaseExpired(UUID offerId, Instant now) {
        int released = db.queryForObject("""
                SELECT COALESCE(SUM(quantity),0) FROM reservation_orders
                WHERE offer_id=? AND status='AWAITING_PAYMENT' AND expires_at<=?
                """, Integer.class, offerId, Timestamp.from(now));
        db.update("""
                UPDATE reservation_orders SET status='CANCELLED', cancel_reason='RESERVATION_EXPIRED'
                WHERE offer_id=? AND status='AWAITING_PAYMENT' AND expires_at<=?
                """, offerId, Timestamp.from(now));
        if (released > 0) {
            int changed = db.update("""
                    UPDATE offers SET reserved_portions=reserved_portions-?,version=version+1,updated_at=?
                    WHERE id=? AND reserved_portions>=?
                    """, released, Timestamp.from(now), offerId, released);
            if (changed != 1) throw new IllegalStateException("Нарушен счетчик временных броней: " + offerId);
            afterCommit(() -> log.info("Expired {} unpaid reservations for offer {}", released, offerId));
        }
    }

    private void lockOffer(UUID id) {
        if (db.queryForList("SELECT id FROM offers WHERE id=? FOR UPDATE", id).isEmpty())
            throw new ApiError(HttpStatus.NOT_FOUND, "Набор не найден.");
    }

    private Reservation readOwned(UUID id, UUID customerId) {
        return db.query("SELECT * FROM reservation_orders WHERE id=? AND customer_id=?", MAPPER, id, customerId)
                .stream().findFirst().orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Заказ не найден."));
    }

    private void requireCustomer(UUID id) {
        if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE id=? AND role='CUSTOMER' AND blocked=FALSE AND verified=TRUE",
                Integer.class, id) != 1)
            throw new ApiError(HttpStatus.FORBIDDEN, "Бронирование доступно подтвержденным покупателям.");
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
