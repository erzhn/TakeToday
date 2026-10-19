package com.example.taketoday.offer;

import com.example.taketoday.catalog.CatalogFilter;
import com.example.taketoday.common.AppTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Repository
public class OfferRepository {
    public static final int PAGE_SIZE = 12;
    private final JdbcTemplate db;
    private static final String SELECT = "SELECT o.*, e.name AS establishment_name, e.address FROM offers o JOIN establishments e ON e.id=o.establishment_id JOIN accounts a ON a.id=e.owner_id ";
    private static final RowMapper<Offer> MAPPER = (rs, n) -> new Offer(
            rs.getObject("id", UUID.class), rs.getObject("establishment_id", UUID.class),
            rs.getString("establishment_name"), rs.getString("address"), rs.getString("title"),
            rs.getString("composition"), FoodType.valueOf(rs.getString("food_type")), rs.getString("photo_path"),
            rs.getInt("regular_price"), rs.getInt("discount_price"), rs.getInt("total_portions"),
            rs.getInt("reserved_portions"), rs.getInt("sold_portions"),
            rs.getTimestamp("pickup_start").toInstant(), rs.getTimestamp("pickup_end").toInstant(), rs.getString("status"));

    public OfferRepository(JdbcTemplate db) { this.db = db; }

    /** Окно фильтра должно пересекаться с оставшимся временем выдачи сегодня или завтра. */
    public List<Offer> findForCatalog(CatalogFilter f, Instant now) {
        var sql = new StringBuilder(SELECT).append("WHERE o.status='ACTIVE' AND a.blocked=FALSE AND o.pickup_end > ?");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(now));
        if (!f.getQ().isBlank()) {
            sql.append(" AND LOWER(o.title) LIKE ? ESCAPE '!'");
            args.add("%" + f.getQ().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (f.getType() != null) { sql.append(" AND o.food_type=?"); args.add(f.getType().name()); }
        if (f.getMinPrice() != null) { sql.append(" AND o.discount_price>=?"); args.add(f.getMinPrice()); }
        if (f.getMaxPrice() != null) { sql.append(" AND o.discount_price<=?"); args.add(f.getMaxPrice()); }
        sql.append(" AND ((? >= ? AND o.pickup_start <= ? AND o.pickup_end > ?) OR (? >= ? AND o.pickup_start <= ? AND o.pickup_end > ?))");
        LocalDate today = now.atZone(AppTime.ZONE).toLocalDate();
        for (int day = 0; day < 2; day++) {
            LocalDate date = today.plusDays(day);
            Instant start = date.atTime(f.getPickupAfter() == null ? LocalTime.MIN : f.getPickupAfter()).atZone(AppTime.ZONE).toInstant();
            Instant end = f.getPickupBefore() == null ? date.plusDays(1).atStartOfDay(AppTime.ZONE).toInstant().minusNanos(1)
                    : date.atTime(f.getPickupBefore()).atZone(AppTime.ZONE).toInstant();
            args.add(Timestamp.from(end));
            args.add(Timestamp.from(now));
            args.add(Timestamp.from(end));
            args.add(Timestamp.from(start.isAfter(now) ? start : now));
        }
        sql.append(" ORDER BY o.pickup_start, o.discount_price, o.id LIMIT ? OFFSET ?");
        args.add(PAGE_SIZE + 1);
        args.add(f.getPage() * PAGE_SIZE);
        return db.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<Offer> getPublic(UUID id) {
        return db.query(SELECT + "WHERE o.id=? AND o.status<>'HIDDEN' AND a.blocked=FALSE", MAPPER, id).stream().findFirst();
    }
}
