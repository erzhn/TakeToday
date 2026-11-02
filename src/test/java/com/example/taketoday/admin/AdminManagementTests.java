package com.example.taketoday.admin;

import com.example.taketoday.stage1.Stage1TestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminManagementTests extends Stage1TestSupport {

    @Test
    void adminCanOpenAllManagementSectionsAndOtherRolesCannot() throws Exception {
        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        UUID buyer = account("buyer@example.com", "Покупатель", "CUSTOMER");

        for (String path : new String[]{"/admin", "/admin/users", "/admin/offers", "/admin/payments", "/admin/refunds"}) {
            mvc.perform(get(path).with(as(admin))).andExpect(status().isOk());
            mvc.perform(get(path).with(as(buyer))).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanHideAndRestoreOffer() throws Exception {
        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        UUID staff = staffWithVenue("staff@example.com", "Пекарня");
        UUID offer = offer((UUID) venueOf(staff).get("id"), "Набор для проверки", "ACTIVE");

        mvc.perform(post("/admin/offers/{id}/hide", offer).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/offers"));
        assertEquals("HIDDEN", db.queryForObject("SELECT status FROM offers WHERE id=?", String.class, offer));
        mvc.perform(get("/admin/offers").with(as(admin))).andExpect(content().string(containsString("Скрыто администратором")));

        mvc.perform(post("/admin/offers/{id}/restore", offer).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals("ACTIVE", db.queryForObject("SELECT status FROM offers WHERE id=?", String.class, offer));
    }

    @Test
    void adminCanBlockAndUnblockCustomerAndCannotBlockAdmin() throws Exception {
        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        UUID buyer = account("buyer@example.com", "Покупатель", "CUSTOMER");
        int version = ((Number) accountRow(buyer).get("auth_version")).intValue();

        mvc.perform(post("/admin/users/{id}/block", buyer).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue((Boolean) accountRow(buyer).get("blocked"));
        assertEquals(version + 1, ((Number) accountRow(buyer).get("auth_version")).intValue());

        mvc.perform(post("/admin/users/{id}/unblock", buyer).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(!(Boolean) accountRow(buyer).get("blocked"));

        mvc.perform(post("/admin/users/{id}/block", admin).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(!(Boolean) accountRow(admin).get("blocked"));
    }

    @Test
    void adminCanViewPaymentsAndCompletePendingRefund() throws Exception {
        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        UUID buyer = account("buyer@example.com", "Покупатель", "CUSTOMER");
        UUID staff = staffWithVenue("staff@example.com", "Пекарня");
        UUID offer = offer((UUID) venueOf(staff).get("id"), "Возврат для проверки", "ACTIVE");
        UUID order = UUID.randomUUID();
        Instant now = Instant.now();
        db.update("""
                INSERT INTO reservation_orders (id, customer_id, offer_id, request_key, title, establishment_name,
                    address, amount, quantity, unit_price, pickup_start, pickup_end, pickup_code, status,
                    created_at, expires_at, cancel_reason, refund_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 500, 1, 500, ?, ?, NULL, 'CANCELLED', ?, ?, 'CUSTOMER', 'PENDING')
                """, order, buyer, offer, UUID.randomUUID(), "Возврат для проверки", "Пекарня", "Бишкек",
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(120)));
        db.update("""
                INSERT INTO payments (id, order_id, amount, status, provider_payment_id, card_last4, created_at, completed_at)
                VALUES (?, ?, 500, 'SUCCEEDED', ?, '4242', ?, ?)
                """, UUID.randomUUID(), order, "mock-admin-payment", Timestamp.from(now.minusSeconds(50)), Timestamp.from(now.minusSeconds(40)));

        mvc.perform(get("/admin/payments").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("mock-admin-payment")))
                .andExpect(content().string(containsString("SUCCEEDED")));
        mvc.perform(get("/admin/refunds").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ожидает обработки")));
        mvc.perform(post("/admin/refunds/{id}/complete", order).with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/refunds"));
        assertEquals("COMPLETED", db.queryForObject("SELECT refund_status FROM reservation_orders WHERE id=?", String.class, order));
        assertEquals(1, ((Number) db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE event_type='ADMIN_REFUND_COMPLETED' AND order_id=?", Integer.class, order)).intValue());
    }

    private UUID offer(UUID venue, String title, String status) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600);
        db.update("""
                INSERT INTO offers (id, establishment_id, title, composition, food_type, photo_path,
                    regular_price, discount_price, total_portions, pickup_start, pickup_end, status)
                VALUES (?, ?, ?, 'Состав', 'BAKERY', '/images/offers/bakery.jpg', 700, 500, 10, ?, ?, ?)
                """, id, venue, title, Timestamp.from(start), Timestamp.from(start.plusSeconds(7200)), status);
        return id;
    }
}
