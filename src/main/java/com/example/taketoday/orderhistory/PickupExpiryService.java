package com.example.taketoday.orderhistory;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.payment.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Статус «Время получения истекло» (п. 4.8.2 ТЗ, SPEC раздел 10): оплаченный заказ, который не забрали
 * до конца окна выдачи, переходит из PAID в PICKUP_EXPIRED.
 *
 * <p>Каждый заказ — отдельная транзакция: ошибка одного не мешает остальным. Перевод — один UPDATE
 * с условием status = 'PAID', поэтому одновременное «Подтвердить выдачу» на кассе не теряется:
 * выдача и истечение ждут друг друга на блокировке строки, и выигрывает только один.
 * Порции и деньги не трогаем: еда продана, заказ просто не забрали.
 */
@Service
public class PickupExpiryService {

    public static final String PICKUP_EXPIRED = "PICKUP_EXPIRED";

    private static final Logger log = LoggerFactory.getLogger(PickupExpiryService.class);

    private record Due(UUID id, Instant pickupEnd) {
    }

    private final JdbcTemplate db;
    private final Clock clock;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    public PickupExpiryService(JdbcTemplate db, Clock clock, AuditLog audit, PlatformTransactionManager manager) {
        this.db = db;
        this.clock = clock;
        this.audit = audit;
        this.transactions = new TransactionTemplate(manager);
    }

    /** @return сколько заказов перевели в «Время получения истекло» */
    public int expireDue() {
        Timestamp now = Timestamp.from(clock.instant());
        // Тот же момент, что и на странице выдачи: «истекло», когда сейчас строго позже конца окна
        List<Due> due = db.query("""
                        SELECT id, pickup_end FROM reservation_orders
                        WHERE status = 'PAID' AND pickup_end < ?
                        ORDER BY pickup_end
                        """,
                (rs, n) -> new Due(rs.getObject("id", UUID.class), rs.getTimestamp("pickup_end").toInstant()), now);
        int expired = 0;
        for (Due order : due) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(tx -> expire(order, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Не удалось перевести заказ {} в «Время получения истекло»", order.id(), e);
            }
        }
        if (expired > 0) {
            log.info("Pickup window expired for {} paid orders", expired);
        }
        return expired;
    }

    private boolean expire(Due order, Timestamp now) {
        int changed = db.update("""
                UPDATE reservation_orders SET status = 'PICKUP_EXPIRED'
                WHERE id = ? AND status = 'PAID' AND pickup_end < ?
                """, order.id(), now);
        if (changed != 1) {
            return false; // заказ успели выдать или отменить
        }
        audit.log(PICKUP_EXPIRED, order.id(), null, null,
                "Время получения истекло: заказ не забрали до " + AppTime.format(order.pickupEnd()));
        return true;
    }
}
