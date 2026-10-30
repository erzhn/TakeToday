package com.example.taketoday.stage4;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.pickup.PickupStats;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Этап 4 (Эржан): проверка кода и подтверждение выдачи (п. 4.7.3–4.7.5, 5.3.2 ТЗ; SPEC, раздел 9). */
class PickupTests extends PaymentTestSupport {

    @Autowired
    PaymentService payments;

    /** Заказ, оплаченный через учебную службу, как в жизни. */
    private UUID paidOrder(int quantity) {
        UUID id = reserve(quantity);
        String url = payments.start(id, customer);
        assertEquals(PaymentResult.PAID, payments.complete(url.substring("/mock-pay/".length()), true, "4242", null));
        return id;
    }

    private String code(UUID orderId) {
        return (String) order(orderId).get("pickup_code");
    }

    private String check(UUID staffId, String code) throws Exception {
        return mvc.perform(post("/staff/pickup/check").with(as(staffId)).with(csrf()).param("code", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- Выдача ----------

    @Test
    void staffChecksCodeAndConfirmsIssue() throws Exception {
        UUID id = paidOrder(2);
        String code = code(id);

        String page = check(staff, code.substring(0, 3) + " " + code.substring(3)); // «482 913» как на экране
        assertContains(page, "Код верный — заказ можно выдать");
        assertContains(page, "Набор выпечки на вечер");
        assertContains(page, "Айбек Д.");                  // имя покупателя сокращено
        assertContains(page, "/staff/pickup/" + id + "/confirm");
        assertEquals("PAID", order(id).get("status"), "проверка ничего не меняет");

        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()))
                .andExpect(redirectedUrl("/staff/pickup"))
                .andExpect(flash().attribute("success", containsString("выдан")));

        assertEquals("ISSUED", order(id).get("status"));
        assertEquals(staff, order(id).get("issued_by"));
        assertNotNull(order(id).get("issued_at"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'ORDER_ISSUED'", id));

        // Покупатель видит «Заказ выдан», кода и QR больше нет
        mvc.perform(get("/orders/" + id).with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Заказ выдан")))
                .andExpect(content().string(not(containsString("qr.png"))));
        mvc.perform(get("/orders/" + id + "/qr.png").with(as(customer))).andExpect(status().isNotFound());
        mvc.perform(get("/reservations/" + id).with(as(customer))).andExpect(redirectedUrl("/orders/" + id));
    }

    @Test
    void sameCodeCannotBeIssuedTwice() throws Exception {
        UUID id = paidOrder(1);
        String code = code(id);
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()));

        String again = check(staff, code);
        assertContains(again, "Заказ уже выдан");
        assertNotContains(again, "/staff/pickup/" + id + "/confirm");

        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()))
                .andExpect(redirectedUrl("/staff/pickup"))
                .andExpect(flash().attribute("pickupError", containsString("Заказ уже выдан")));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'ORDER_ISSUED'", id));
    }

    @Test
    void qrCodeContentIsAccepted() throws Exception {
        UUID id = paidOrder(1);
        assertContains(check(staff, "TAKETODAY:" + id + ":" + code(id)), "Код верный");
        assertContains(check(staff, "TAKETODAY:" + UUID.randomUUID() + ":" + code(id)), "Код не найден");
    }

    // ---------- Отказы (порядок и тексты — SPEC, раздел 9) ----------

    @Test
    void wrongFormatAndUnknownCode() throws Exception {
        UUID id = paidOrder(1);
        assertContains(check(staff, "12345"), "Введите 6 цифр кода");
        assertContains(check(staff, "abcdef"), "Введите 6 цифр кода");
        String unknown = code(id).equals("000000") ? "000001" : "000000";
        assertContains(check(staff, unknown), "Код не найден. Проверьте цифры");
    }

    @Test
    void otherVenueCannotSeeOrIssue() throws Exception {
        UUID id = paidOrder(1);
        UUID otherStaff = staffWithVenue("cafe@example.com", "Кофейня «Утро»");
        String page = check(otherStaff, code(id));
        assertContains(page, "Этот заказ из другого заведения");
        assertNotContains(page, "Набор выпечки на вечер");
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(otherStaff)).with(csrf()))
                .andExpect(flash().attribute("pickupError", "Заказ не найден в вашем заведении"));
        assertEquals("PAID", order(id).get("status"));
    }

    @Test
    void pickupTimeOverCannotBeIssued() throws Exception {
        UUID id = paidOrder(1);
        String code = code(id);
        advance(3 * 24 * 3600); // прошло окно выдачи
        assertContains(check(staff, code), "Время получения истекло");
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()))
                .andExpect(flash().attribute("pickupError", "Время получения истекло"));
        assertEquals("PAID", order(id).get("status"));
    }

    @Test
    void cancelledOrderIsRefused() throws Exception {
        UUID id = paidOrder(1);
        db.update("UPDATE reservation_orders SET status = 'CANCELLED', cancel_reason = 'VENUE' WHERE id = ?", id);
        assertContains(check(staff, code(id)), "Заказ отменен");
    }

    @Test
    void earlyPickupIsAllowedWithWarning() throws Exception {
        UUID id = paidOrder(1); // выдача завтра с 19:00, а сейчас — сегодня
        String page = check(staff, code(id));
        assertContains(page, "Окно выдачи начнётся в 19:00");
        assertContains(page, "Подтвердить выдачу");
    }

    @Test
    void guessingCodesIsLimited() throws Exception {
        UUID id = paidOrder(1);
        String right = code(id);
        int wrong = 0;
        for (int n = 100000; wrong < 10; n++) {
            String guess = String.valueOf(n);
            if (guess.equals(right)) continue;
            check(staff, guess);
            wrong++;
        }
        assertContains(check(staff, right), "Слишком много неверных кодов");
    }

    // ---------- Доступ ----------

    @Test
    void onlyStaffOpensPickup() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(get("/staff/pickup")).andExpect(status().isUnauthorized());
        mvc.perform(get("/staff/pickup").with(as(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(customer)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff))).andExpect(status().isForbidden()); // без CSRF
        assertEquals("PAID", order(id).get("status"));
        mvc.perform(get("/staff/pickup").with(as(staff))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Включить камеру")))
                .andExpect(content().string(containsString("Ожидают выдачи")));
    }

    // ---------- Цифры за день ----------

    @Test
    void dashboardShowsTodayNumbers() throws Exception {
        UUID first = paidOrder(1);   // 250 сом
        paidOrder(2);                // 500 сом
        reserve(1);                  // не оплачен — не считается
        mvc.perform(post("/staff/pickup/" + first + "/confirm").with(as(staff)).with(csrf()));

        PickupStats expected = new PickupStats(2, 750, 1, 1);
        mvc.perform(get("/staff").with(as(staff))).andExpect(status().isOk())
                .andExpect(model().attribute("stats", expected));
        mvc.perform(get("/staff/pickup").with(as(staff)))
                .andExpect(model().attribute("stats", expected));
    }

    // ---------- База ----------

    @Test
    void orderStatusesAreCheckedByDatabase() {
        UUID id = paidOrder(1);
        db.update("UPDATE reservation_orders SET status = 'PICKUP_EXPIRED' WHERE id = ?", id);
        assertThrows(DataIntegrityViolationException.class,
                () -> db.update("UPDATE reservation_orders SET status = 'LOST' WHERE id = ?", id));
    }

    private static void assertContains(String page, String text) {
        org.junit.jupiter.api.Assertions.assertTrue(page.contains(text), () -> "нет текста «" + text + "»");
    }

    private static void assertNotContains(String page, String text) {
        org.junit.jupiter.api.Assertions.assertFalse(page.contains(text), () -> "лишний текст «" + text + "»");
    }
}
