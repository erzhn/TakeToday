package com.example.taketoday.orderhistory;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.stage1.Stage1TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.notifications.enabled=false")
class OrderHistoryPageTests extends Stage1TestSupport {

    private UUID customer;
    private UUID otherCustomer;
    private UUID staff;
    private UUID otherStaff;
    private UUID customerOrder;
    private UUID otherOrder;

    @BeforeEach
    void prepareOrders() {
        customer = account("buyer@example.com", "Айбек Джумабаев", "CUSTOMER");
        otherCustomer = account("other@example.com", "Другой покупатель", "CUSTOMER");
        staff = staffWithVenue("staff@example.com", "Пекарня «Тандыр»");
        otherStaff = staffWithVenue("other-staff@example.com", "Другое кафе");
        UUID venue = (UUID) venueOf(staff).get("id");
        UUID otherVenue = (UUID) venueOf(otherStaff).get("id");
        Instant start = LocalDate.now(AppTime.ZONE).plusDays(1).atTime(19, 0).atZone(AppTime.ZONE).toInstant();
        UUID offer = offer(venue, "Набор первого заведения");
        UUID otherOffer = offer(otherVenue, "Набор второго заведения");
        customerOrder = order(customer, offer, "Заказ покупателя", start);
        otherOrder = order(otherCustomer, otherOffer, "Чужой заказ", start);
    }

    @Test
    void customerSeesOnlyOwnCurrentAndPastOrders() throws Exception {
        mvc.perform(get("/orders").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Заказ покупателя")))
                .andExpect(content().string(containsString("676 889")))
                .andExpect(content().string(containsString("Открыть QR")))
                .andExpect(content().string(not(containsString("Чужой заказ"))));
    }

    @Test
    void paidOrderAfterPickupWindowIsCompletedAsExpired() throws Exception {
        UUID venue = (UUID) venueOf(staff).get("id");
        UUID expiredOffer = offer(venue, "Просроченный набор");
        order(customer, expiredOffer, "Просроченный заказ", Instant.now().minusSeconds(7200));

        mvc.perform(get("/orders").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Просроченный заказ")))
                .andExpect(content().string(containsString("Время получения истекло")));
    }

    @Test
    void staffSeesOnlyOrdersFromOwnEstablishment() throws Exception {
        mvc.perform(get("/staff/orders").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("buyer@example.com")))
                .andExpect(content().string(not(containsString("other@example.com"))));
    }

    @Test
    void rolesCannotOpenTheOtherHistoryPage() throws Exception {
        mvc.perform(get("/orders").with(as(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/orders").with(as(customer))).andExpect(status().isForbidden());
    }

    private UUID offer(UUID venue, String title) {
        UUID id = UUID.randomUUID();
        Instant start = LocalDate.now(AppTime.ZONE).plusDays(1).atTime(19, 0).atZone(AppTime.ZONE).toInstant();
        db.update("""
                INSERT INTO offers (id, establishment_id, title, composition, food_type, photo_path, regular_price,
                                    discount_price, total_portions, pickup_start, pickup_end, status)
                VALUES (?, ?, ?, 'Состав набора', 'BAKERY', '/images/offers/bakery.jpg', 500, 250, 5, ?, ?, 'ACTIVE')
                """, id, venue, title, Timestamp.from(start), Timestamp.from(start.plusSeconds(7200)));
        return id;
    }

    private UUID order(UUID owner, UUID offer, String title, Instant pickupStart) {
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO reservation_orders (id, customer_id, offer_id, request_key, title, establishment_name,
                    address, amount, quantity, unit_price, pickup_start, pickup_end, pickup_code, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, 'Заведение', 'Бишкек', 250, 1, 250, ?, ?, '676889', 'PAID', ?, ?)
                """, id, owner, offer, UUID.randomUUID(), title, Timestamp.from(pickupStart),
                Timestamp.from(pickupStart.plusSeconds(7200)), Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plusSeconds(180)));
        return id;
    }
}
