package com.example.taketoday.orderhistory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderHistoryService {

    private final JdbcTemplate db;
    private final Clock clock;

    public OrderHistoryService(JdbcTemplate db, @Qualifier("reservationClock") Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public List<OrderHistoryEntry> customerOrders(UUID customerId) {
        Instant now = clock.instant();
        return db.query(BASE_SQL + " WHERE r.customer_id = ? " + ORDER_SQL,
                MAPPER, customerId, Timestamp.from(now));
    }

    public List<OrderHistoryEntry> staffOrders(UUID staffId) {
        Instant now = clock.instant();
        return db.query(BASE_SQL + " JOIN offers o2 ON o2.id = r.offer_id "
                        + "JOIN establishments e2 ON e2.id = o2.establishment_id WHERE e2.owner_id = ? " + ORDER_SQL,
                MAPPER, staffId, Timestamp.from(now));
    }

    private static final String BASE_SQL = """
            SELECT r.id, r.title, r.establishment_name, r.address, r.amount, r.quantity,
                   r.created_at, r.pickup_start, r.pickup_end, r.pickup_code, r.status, r.cancel_reason, r.refund_status,
                   a.name AS customer_name, a.email AS customer_email
            FROM reservation_orders r
            JOIN accounts a ON a.id = r.customer_id
            """;

    private static final String ORDER_SQL = """
            ORDER BY CASE WHEN (r.status = 'AWAITING_PAYMENT' OR (r.status = 'PAID' AND r.pickup_end > ?))
                          THEN 0 ELSE 1 END,
                     r.created_at DESC
            """;

    private static final org.springframework.jdbc.core.RowMapper<OrderHistoryEntry> MAPPER = (rs, row) ->
            new OrderHistoryEntry(
                    rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("establishment_name"),
                    rs.getString("address"), rs.getInt("amount"), rs.getInt("quantity"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("pickup_start").toInstant(),
                    rs.getTimestamp("pickup_end").toInstant(), rs.getString("pickup_code"), rs.getString("status"),
                    rs.getString("cancel_reason"), rs.getString("refund_status"),
                    rs.getString("customer_name"), rs.getString("customer_email"));
}
