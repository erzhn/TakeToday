package com.example.taketoday.offer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Таблица offers со стороны заведения: создание, изменение, снятие с продажи.
 * Счётчики брони меняет ReservationService (Жаныбек), оплаты — PaymentService (Эржан).
 * Поиск для каталога — в {@link OfferRepository} (Жаныбек), здесь его не дублируем.
 *
 * Счётчики порций (reserved / sold) меняются только методами этого класса, и каждый — одним UPDATE
 * с условием в WHERE. База выполняет такие запросы по очереди, поэтому одну порцию нельзя продать дважды
 * (SPEC раздел 7, п. 4.5.3 ТЗ).
 */
@Repository
public class StaffOfferRepository {

    static final RowMapper<StaffOffer> MAPPER = (rs, n) -> new StaffOffer(
            rs.getObject("id", UUID.class),
            rs.getObject("establishment_id", UUID.class),
            rs.getString("title"),
            rs.getString("composition"),
            FoodType.valueOf(rs.getString("food_type")),
            rs.getString("photo_path"),
            rs.getInt("regular_price"),
            rs.getInt("discount_price"),
            rs.getInt("total_portions"),
            rs.getInt("reserved_portions"),
            rs.getInt("sold_portions"),
            rs.getTimestamp("pickup_start").toInstant(),
            rs.getTimestamp("pickup_end").toInstant(),
            OfferStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version"));

    private final JdbcTemplate db;

    public StaffOfferRepository(JdbcTemplate db) {
        this.db = db;
    }

    /** Сколько предложений используют этот файл фото («Повторить» берёт то же фото). */
    public int countByPhoto(String photoPath) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM offers WHERE photo_path = ?", Integer.class, photoPath);
        return count == null ? 0 : count;
    }

    public Optional<StaffOffer> findById(UUID id) {
        return db.query("SELECT * FROM offers WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** Предложение заведения: чужое не найдётся (п. 5.2.3 ТЗ). */
    public Optional<StaffOffer> findByIdAndEstablishment(UUID id, UUID establishmentId) {
        return db.query("SELECT * FROM offers WHERE id = ? AND establishment_id = ?", MAPPER, id, establishmentId)
                .stream().findFirst();
    }

    /** Все предложения заведения: сначала самые поздние по времени выдачи. */
    public List<StaffOffer> findByEstablishment(UUID establishmentId) {
        return db.query("SELECT * FROM offers WHERE establishment_id = ? ORDER BY pickup_end DESC, created_at DESC",
                MAPPER, establishmentId);
    }

    public void insert(StaffOffer o) {
        db.update("""
                        INSERT INTO offers (id, establishment_id, title, composition, food_type, photo_path,
                                            regular_price, discount_price, total_portions, reserved_portions, sold_portions,
                                            pickup_start, pickup_end, status, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, 0)
                        """,
                o.id(), o.establishmentId(), o.title(), o.composition(), o.foodType().name(), o.photoPath(),
                o.regularPrice(), o.discountPrice(), o.totalPortions(),
                ts(o.pickupStart()), ts(o.pickupEnd()), o.status().name(), ts(o.createdAt()), ts(o.updatedAt()));
    }

    /**
     * Изменение сотрудником. Обновится только если предложение этого заведения
     * и новых порций не меньше, чем уже забронировано и оплачено. Проверка — в том же UPDATE,
     * поэтому одновременная бронь покупателя (ReservationService, Жаныбек) не даст продать больше, чем есть.
     *
     * @return true, если строка обновлена
     */
    public boolean updateByStaff(StaffOffer o) {
        return db.update("""
                        UPDATE offers
                        SET title = ?, composition = ?, food_type = ?, photo_path = ?,
                            regular_price = ?, discount_price = ?, total_portions = ?,
                            pickup_start = ?, pickup_end = ?, updated_at = ?, version = version + 1
                        WHERE id = ? AND establishment_id = ?
                          AND reserved_portions + sold_portions <= ?
                        """,
                o.title(), o.composition(), o.foodType().name(), o.photoPath(),
                o.regularPrice(), o.discountPrice(), o.totalPortions(),
                ts(o.pickupStart()), ts(o.pickupEnd()), ts(Instant.now()),
                o.id(), o.establishmentId(), o.totalPortions()) == 1;
    }

    /** Смена состояния, только если сейчас оно равно from (снять / вернуть в продажу). */
    public boolean changeStatus(UUID id, UUID establishmentId, OfferStatus from, OfferStatus to) {
        return db.update("""
                        UPDATE offers SET status = ?, updated_at = ?, version = version + 1
                        WHERE id = ? AND establishment_id = ? AND status = ?
                        """,
                to.name(), ts(Instant.now()), id, establishmentId, from.name()) == 1;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    /** Этап 5: причины скрытия администратором (id предложения → причина). */
    public java.util.Map<UUID, String> hiddenReasons(UUID establishmentId) {
        java.util.Map<UUID, String> reasons = new java.util.HashMap<>();
        db.query("SELECT id, hidden_reason FROM offers WHERE establishment_id = ? AND status = 'HIDDEN' AND hidden_reason IS NOT NULL",
                rs -> { reasons.put(rs.getObject(1, UUID.class), rs.getString(2)); }, establishmentId);
        return reasons;
    }
}
