package com.example.taketoday.stage3;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.reservation.ReservationService;
import com.example.taketoday.stage1.Stage1TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;

/** Подготовка для тестов этапа 3: покупатель, заведение с набором и управляемые часы брони. */
@TestPropertySource(properties = "app.reservations.expiry-enabled=false")
public abstract class PaymentTestSupport extends Stage1TestSupport {

    @MockitoBean(name = "reservationClock")
    protected Clock clock;

    @Autowired
    protected ReservationService reservations;

    protected Instant now;
    protected UUID customer;
    protected UUID otherCustomer;
    protected UUID staff;
    protected UUID offer;

    @BeforeEach
    protected void prepareOffer() {
        now = Instant.now();
        when(clock.instant()).thenReturn(now);
        customer = account("buyer@example.com", "Айбек Джумабаев", "CUSTOMER");
        otherCustomer = account("other-buyer@example.com", "Другой покупатель", "CUSTOMER");
        staff = staffWithVenue("staff@example.com", "Пекарня «Тандыр»");
        UUID venue = (UUID) venueOf(staff).get("id");
        offer = UUID.randomUUID();
        Instant start = LocalDate.now(AppTime.ZONE).plusDays(1).atTime(19, 0).atZone(AppTime.ZONE).toInstant();
        db.update("""
                        INSERT INTO offers (id, establishment_id, title, composition, food_type, photo_path, regular_price,
                                            discount_price, total_portions, pickup_start, pickup_end, status)
                        VALUES (?, ?, 'Набор выпечки на вечер', 'Самса, булочки', 'BAKERY', '/images/offers/bakery.jpg',
                                500, 250, 5, ?, ?, 'ACTIVE')
                        """, offer, venue, Timestamp.from(start), Timestamp.from(start.plusSeconds(7200)));
    }

    protected void advance(long seconds) {
        when(clock.instant()).thenReturn(now.plusSeconds(seconds));
    }

    protected UUID reserve(int quantity) {
        return reservations.create(customer, offer, UUID.randomUUID(), quantity).id();
    }

    protected Map<String, Object> order(UUID id) {
        return db.queryForMap("SELECT * FROM reservation_orders WHERE id = ?", id);
    }

    protected Map<String, Object> offerRow() {
        return db.queryForMap("SELECT * FROM offers WHERE id = ?", offer);
    }

    protected int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }
}
