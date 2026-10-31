package com.example.taketoday.stage5;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Этап 5 (Эржан): админ-панель — пользователи, заведения, предложения, платежи, возвраты (п. 4.10 ТЗ). */
class AdminPanelTests extends PaymentTestSupport {

    @Autowired
    PaymentService payments;

    UUID admin;

    @BeforeEach
    void createAdmin() {
        admin = account("admin@example.com", "Администратор Сайта", "ADMIN");
    }

    private UUID paidOrder(int quantity) {
        UUID id = reserve(quantity);
        String provider = payments.start(id, customer).substring("/mock-pay/".length());
        assertEquals(PaymentResult.PAID, payments.complete(provider, true, "4242", null));
        return id;
    }

    // ---------- Доступ ----------

    @Test
    void onlyAdminOpensPanel() throws Exception {
        for (String page : new String[]{"/admin/users", "/admin/venues", "/admin/offers", "/admin/payments",
                "/admin/refunds", "/admin/audit"}) {
            mvc.perform(get(page)).andExpect(status().isUnauthorized());
            mvc.perform(get(page).with(as(customer))).andExpect(status().isForbidden());
            mvc.perform(get(page).with(as(staff))).andExpect(status().isForbidden());
            mvc.perform(get(page).with(as(admin))).andExpect(status().isOk());
        }
        mvc.perform(post("/admin/users/" + customer + "/block").with(as(staff)).with(csrf()).param("reason", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + customer + "/block").with(as(admin)).param("reason", "x"))
                .andExpect(status().isForbidden()); // без CSRF
        assertEquals(false, db.queryForObject("SELECT blocked FROM accounts WHERE id = ?", Boolean.class, customer));
    }

    // ---------- Пользователи ----------

    @Test
    void adminBlocksAndUnblocksUser() throws Exception {
        var buyerSession = as(customer); // сессия покупателя, открытая до блокировки
        mvc.perform(get("/admin/users").with(as(admin)).param("q", "buyer@"))
                .andExpect(content().string(containsString("buyer@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("staff@example.com"))));

        mvc.perform(post("/admin/users/" + customer + "/block").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("adminError", containsString("Укажите причину")));
        mvc.perform(post("/admin/users/" + customer + "/block").with(as(admin)).with(csrf()).param("reason", "Спам заказами"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("success", containsString("заблокирована")));
        assertEquals(true, db.queryForObject("SELECT blocked FROM accounts WHERE id = ?", Boolean.class, customer));
        assertEquals("Спам заказами", db.queryForObject("SELECT blocked_reason FROM accounts WHERE id = ?", String.class, customer));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE event_type = 'USER_BLOCKED' AND user_id = ?", admin));

        // Открытая сессия больше не работает
        mvc.perform(get("/profile").with(buyerSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users").with(as(admin)).param("role", "BLOCKED"))
                .andExpect(content().string(containsString("Спам заказами")));

        mvc.perform(post("/admin/users/" + customer + "/unblock").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "Учётная запись разблокирована."));
        assertEquals(false, db.queryForObject("SELECT blocked FROM accounts WHERE id = ?", Boolean.class, customer));
    }

    @Test
    void adminCannotBlockAdmins() throws Exception {
        UUID other = account("admin2@example.com", "Второй админ", "ADMIN");
        mvc.perform(post("/admin/users/" + admin + "/block").with(as(admin)).with(csrf()).param("reason", "x"))
                .andExpect(flash().attribute("adminError", "Нельзя заблокировать самого себя."));
        mvc.perform(post("/admin/users/" + other + "/block").with(as(admin)).with(csrf()).param("reason", "x"))
                .andExpect(flash().attribute("adminError", "Администратора заблокировать нельзя."));
    }

    @Test
    void blockedVenueDisappearsFromCatalog() throws Exception {
        mvc.perform(get("/offers/" + offer)).andExpect(status().isOk());
        mvc.perform(post("/admin/users/" + staff + "/block").with(as(admin)).with(csrf()).param("reason", "Продаёт испорченное"));
        mvc.perform(get("/offers/" + offer)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/venues").with(as(admin)))
                .andExpect(content().string(containsString("Пекарня «Тандыр»")))
                .andExpect(content().string(containsString("Заблокирован")));
    }

    // ---------- Предложения ----------

    @Test
    void adminHidesOfferAndVenueSeesReason() throws Exception {
        mvc.perform(post("/admin/offers/" + offer + "/hide").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("adminError", containsString("Укажите причину")));
        mvc.perform(post("/admin/offers/" + offer + "/hide").with(as(admin)).with(csrf()).param("reason", "Фото не соответствует составу"))
                .andExpect(redirectedUrl("/admin/offers"));
        assertEquals("HIDDEN", offerRow().get("status"));

        mvc.perform(get("/offers/" + offer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/reservations").with(as(customer)).with(csrf()).contentType("application/json")
                        .content("{\"offerId\":\"" + offer + "\",\"requestKey\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(content().string(containsString("Скрыто администратором")))
                .andExpect(content().string(containsString("Фото не соответствует составу")));
        // Сотрудник не может сам вернуть скрытое предложение в продажу
        mvc.perform(post("/staff/offers/" + offer + "/resume").with(as(staff)).with(csrf()));
        assertEquals("HIDDEN", offerRow().get("status"));

        mvc.perform(post("/admin/offers/" + offer + "/unhide").with(as(admin)).with(csrf()));
        assertEquals("WITHDRAWN", offerRow().get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE event_type = 'OFFER_HIDDEN'"));
    }

    // ---------- Платежи, заказы, возвраты ----------

    @Test
    void paymentsAndOrderCardShowEverything() throws Exception {
        UUID id = paidOrder(2);
        String number = id.toString().substring(0, 8).toUpperCase();
        mvc.perform(get("/admin/payments").with(as(admin)).param("q", number.toLowerCase()))
                .andExpect(content().string(containsString("№ " + number)))
                .andExpect(content().string(containsString("500 сом")))
                .andExpect(content().string(containsString("Успешно")))
                .andExpect(content().string(containsString("Карта •••• 4242")));
        mvc.perform(get("/admin/payments").with(as(admin)).param("status", "FAILED"))
                .andExpect(content().string(containsString("Платежей не нашли")));
        mvc.perform(get("/admin/orders/" + id).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Айбек Джумабаев")))
                .andExpect(content().string(containsString("PAYMENT_SUCCEEDED")))
                .andExpect(content().string(containsString("Отменить заказ и вернуть 500 сом")));
        mvc.perform(get("/admin/orders/" + UUID.randomUUID()).with(as(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/audit").with(as(admin)).param("q", number))
                .andExpect(content().string(containsString("PAYMENT_STARTED")));
        mvc.perform(get("/admin").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Всего пользователей")));
    }

    @Test
    void adminCancelsPaidOrderWithRefund() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf()).param("reason", "Жалоба на заведение"))
                .andExpect(flash().attribute("success", "Деньги возвращены покупателю."));
        assertEquals("CANCELLED", order(id).get("status"));
        assertEquals("ADMIN", order(id).get("cancel_reason"));
        assertEquals("COMPLETED", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ? AND initiated_by = 'ADMIN' AND admin_id = ?", id, admin));
        mvc.perform(get("/admin/refunds").with(as(admin)).param("status", "COMPLETED"))
                .andExpect(content().string(containsString("Жалоба на заведение")))
                .andExpect(content().string(containsString("Вручную")));
    }

    @Test
    void adminRefundsIssuedOrderAfterComplaint() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()));
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf()).param("reason", "Набор был испорчен"))
                .andExpect(flash().attribute("success", "Деньги возвращены покупателю."));
        assertEquals("ISSUED", order(id).get("status"), "выданный заказ не отменяется");
        assertEquals("COMPLETED", order(id).get("refund_status"));
    }

    @Test
    void nothingToRefundForUnpaidOrder() throws Exception {
        UUID id = reserve(1);
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf()).param("reason", "Проверка"))
                .andExpect(flash().attribute("adminError", containsString("нечего возвращать")));
        assertEquals(0, count("SELECT COUNT(*) FROM refunds"));
    }

    @Test
    void redirectStaysInsideAdminPanel() throws Exception {
        mvc.perform(post("/admin/users/" + customer + "/block").with(as(admin)).with(csrf())
                        .param("reason", "Тест").param("back", "https://evil.example.com/"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(post("/admin/users/" + customer + "/unblock").with(as(admin)).with(csrf())
                        .param("back", "//evil.example.com"))
                .andExpect(redirectedUrl("/admin/users"));
        assertTrue(true);
    }
}
