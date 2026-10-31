package com.example.taketoday.stage3;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Этап 3 (Эржан): оплата, повтор после ошибки, повторные сообщения, поздняя оплата (п. 4.6, 5.3.2, 5.3.4 ТЗ). */
class PaymentFlowTests extends PaymentTestSupport {

    @Autowired
    PaymentService payments;

    /** «Перейти к оплате» → адрес учебной платёжной страницы. */
    private String startPayment(UUID orderId) throws Exception {
        return mvc.perform(post("/reservations/" + orderId + "/pay").with(as(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/mock-pay/mock_*"))
                .andReturn().getResponse().getRedirectedUrl();
    }

    private MockHttpServletResponse payByCard(String payUrl, String card) throws Exception {
        return mvc.perform(post(payUrl).with(as(customer)).with(csrf())
                        .param("action", "pay").param("cardNumber", card).param("expiry", "12/40").param("cvv", "123"))
                .andReturn().getResponse();
    }

    private String providerId(String payUrl) {
        return payUrl.substring("/mock-pay/".length());
    }

    // ---------- Успешная оплата ----------

    @Test
    void successfulPaymentIssuesCodeAndTurnsHoldIntoSale() throws Exception {
        UUID id = reserve(2);
        String payUrl = startPayment(id);

        mvc.perform(get(payUrl).with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("500")))          // 2 × 250 сом — сумма с сервера
                .andExpect(content().string(containsString("Учебная платёжная служба")));

        MockHttpServletResponse result = payByCard(payUrl, "4242 4242 4242 4242");
        assertEquals("/orders/" + id, result.getRedirectedUrl());

        Map<String, Object> row = order(id);
        assertEquals("PAID", row.get("status"));
        String code = (String) row.get("pickup_code");
        assertTrue(code.matches("\\d{6}"), "код получения — 6 цифр");
        assertNotNull(row.get("paid_at"));
        assertEquals(0, ((Number) offerRow().get("reserved_portions")).intValue());
        assertEquals(2, ((Number) offerRow().get("sold_portions")).intValue());

        Map<String, Object> payment = db.queryForMap("SELECT * FROM payments WHERE order_id = ?", id);
        assertEquals("SUCCEEDED", payment.get("status"));
        assertEquals(500, ((Number) payment.get("amount")).intValue());
        assertEquals("4242", payment.get("card_last4"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'PAYMENT_SUCCEEDED'", id));

        // Экран 4: код и QR
        mvc.perform(get("/orders/" + id).with(as(customer))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Оплата прошла успешно")))
                .andExpect(content().string(containsString(code.substring(0, 3) + " " + code.substring(3))))
                .andExpect(content().string(containsString("/orders/" + id + "/qr.png")))
                .andExpect(content().string(containsString("•••• <span>4242</span>")));
        // Страница брони оплаченного заказа ведёт к коду
        mvc.perform(get("/reservations/" + id).with(as(customer))).andExpect(redirectedUrl("/orders/" + id));
    }

    @Test
    void fullCardNumberAndCvvAreNeverStored() throws Exception {
        UUID id = reserve(1);
        payByCard(startPayment(id), "4242424242424242");
        String everything = db.queryForList("SELECT * FROM payments").toString()
                + db.queryForList("SELECT * FROM audit_log").toString()
                + db.queryForList("SELECT * FROM reservation_orders").toString();
        assertFalse(everything.contains("4242424242424242"));
        assertFalse(everything.contains("424242424242"));
        assertFalse(everything.contains("12/40"));
    }

    // ---------- Ошибка и повтор ----------

    @Test
    void declinedPaymentKeepsHoldAndBuyerCanRetry() throws Exception {
        UUID id = reserve(1);
        String first = startPayment(id);
        MockHttpServletResponse declined = payByCard(first, "4000 0000 0000 0002");
        assertEquals("/reservations/" + id, declined.getRedirectedUrl());

        assertEquals("AWAITING_PAYMENT", order(id).get("status"));
        assertNull(order(id).get("pickup_code"));
        assertEquals(1, ((Number) offerRow().get("reserved_portions")).intValue(), "бронь не снята");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'PAYMENT_FAILED'", id));

        mvc.perform(get("/reservations/" + id).with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Недостаточно средств на карте")))
                .andExpect(content().string(containsString("Попробовать оплатить ещё раз")));

        advance(60); // таймер не сбрасывается и не продлевается
        String second = startPayment(id);
        assertFalse(first.equals(second), "новая попытка — новый платёж");
        payByCard(second, "4242 4242 4242 4242");
        assertEquals("PAID", order(id).get("status"));
        assertEquals(2, count("SELECT COUNT(*) FROM payments WHERE order_id = ?", id));
        assertEquals(1, count("SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCEEDED'", id));
    }

    @Test
    void buyerCanCancelOnPaymentPageAndRetry() throws Exception {
        UUID id = reserve(1);
        String payUrl = startPayment(id);
        mvc.perform(post(payUrl).with(as(customer)).with(csrf()).param("action", "cancel"))
                .andExpect(redirectedUrl("/reservations/" + id));
        assertEquals("AWAITING_PAYMENT", order(id).get("status"));
        assertEquals("FAILED", db.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
    }

    @Test
    void newAttemptMakesOldPaymentPageStale() throws Exception {
        UUID id = reserve(1);
        String old = startPayment(id);
        startPayment(id);
        assertEquals("CANCELLED", db.queryForObject("SELECT status FROM payments WHERE provider_payment_id = ?",
                String.class, providerId(old)));
        mvc.perform(get(old).with(as(customer))).andExpect(redirectedUrl("/reservations/" + id));
        mvc.perform(post(old).with(as(customer)).with(csrf()).param("action", "pay")
                        .param("cardNumber", "4242424242424242").param("expiry", "12/40").param("cvv", "123"))
                .andExpect(redirectedUrl("/reservations/" + id))
                .andExpect(flash().attribute("paymentError", containsString("устарела")));
        assertEquals("AWAITING_PAYMENT", order(id).get("status"));
    }

    @Test
    void wrongCardDataIsShownOnFormAndDoesNotCountAsPayment() throws Exception {
        UUID id = reserve(1);
        String payUrl = startPayment(id);
        mvc.perform(post(payUrl).with(as(customer)).with(csrf()).param("action", "pay")
                        .param("cardNumber", "4242 4242 4242 4241").param("expiry", "01/20").param("cvv", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Проверьте номер карты")))
                .andExpect(content().string(containsString("Срок действия карты истёк")))
                .andExpect(content().string(containsString("CVV — 3 цифры")));
        assertEquals("CREATED", db.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
    }

    // ---------- Повторные сообщения ----------

    @Test
    void repeatedResultForSamePaymentChangesNothing() throws Exception {
        UUID id = reserve(1);
        String provider = providerId(startPayment(id));
        assertEquals(PaymentResult.PAID, payments.complete(provider, true, "4242", null));
        String code = (String) order(id).get("pickup_code");

        assertEquals(PaymentResult.DUPLICATE, payments.complete(provider, true, "4242", null));
        assertEquals(PaymentResult.DUPLICATE, payments.complete(provider, false, null, "Отказ"));

        assertEquals("PAID", order(id).get("status"));
        assertEquals(code, order(id).get("pickup_code"), "код не меняется");
        assertEquals(1, ((Number) offerRow().get("sold_portions")).intValue(), "порция не продана дважды");
        assertEquals("SUCCEEDED", db.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
        assertEquals(2, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'PAYMENT_DUPLICATE'", id));

        // Двойная отправка формы — тоже ничего не ломает
        mvc.perform(post("/mock-pay/" + provider).with(as(customer)).with(csrf()).param("action", "pay")
                        .param("cardNumber", "4242424242424242").param("expiry", "12/40").param("cvv", "123"))
                .andExpect(redirectedUrl("/orders/" + id));
        assertEquals(1, ((Number) offerRow().get("sold_portions")).intValue());
    }

    @Test
    void secondSuccessfulAttemptForPaidOrderIsMarkedForRefund() throws Exception {
        UUID id = reserve(1);
        String first = providerId(startPayment(id));
        String second = providerId(startPayment(id)); // первая попытка заменена, но банк всё же списал по ней
        assertEquals(PaymentResult.PAID, payments.complete(second, true, "4242", null));
        assertEquals(PaymentResult.ALREADY_PAID, payments.complete(first, true, "1111", null));
        assertEquals(1, ((Number) offerRow().get("sold_portions")).intValue());
        assertTrue(db.queryForObject("SELECT error_message FROM payments WHERE provider_payment_id = ?", String.class, first)
                .contains("вернуть"));
        // Этап 5: лишняя сумма возвращается автоматически, заказ остаётся оплаченным
        assertEquals(1, count("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id = r.payment_id "
                + "WHERE p.provider_payment_id = ? AND r.status = 'COMPLETED'", first));
        assertEquals("PAID", order(id).get("status"));
    }

    // ---------- Сумма ----------

    @Test
    void paymentAmountMustMatchOrder() throws Exception {
        UUID id = reserve(3);
        String provider = providerId(startPayment(id));
        assertEquals(750, db.queryForObject("SELECT amount FROM payments WHERE order_id = ?", Integer.class, id));
        db.update("UPDATE payments SET amount = 1 WHERE provider_payment_id = ?", provider);
        assertEquals(PaymentResult.FAILED, payments.complete(provider, true, "4242", null));
        assertEquals("AWAITING_PAYMENT", order(id).get("status"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'ERROR'", id));
    }

    // ---------- Время брони ----------

    @Test
    void paymentCannotStartAfterHoldExpired() throws Exception {
        UUID id = reserve(1);
        advance(181);
        mvc.perform(post("/reservations/" + id + "/pay").with(as(customer)).with(csrf()))
                .andExpect(redirectedUrl("/reservations/" + id))
                .andExpect(flash().attribute("paymentError", containsString("Время на оплату истекло")));
        assertEquals(0, count("SELECT COUNT(*) FROM payments WHERE order_id = ?", id));
        assertEquals("CANCELLED", order(id).get("status"));
        assertEquals("RESERVATION_EXPIRED", order(id).get("cancel_reason"));
        assertEquals(0, ((Number) offerRow().get("reserved_portions")).intValue());
    }

    @Test
    void latePaymentCancelsOrderAndMarksRefund() throws Exception {
        UUID id = reserve(2);
        String payUrl = startPayment(id);
        advance(200); // покупатель долго вводил карту
        MockHttpServletResponse result = payByCard(payUrl, "4242 4242 4242 4242");
        assertEquals("/orders/" + id, result.getRedirectedUrl());

        Map<String, Object> row = order(id);
        assertEquals("CANCELLED", row.get("status"));
        assertEquals("LATE_PAYMENT", row.get("cancel_reason"));
        // Этап 5: деньги возвращаются сразу через учебную платёжную службу
        assertEquals("COMPLETED", row.get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ? AND status = 'COMPLETED' AND amount = 500", id));
        assertNull(row.get("pickup_code"), "кода нет — заказ не выдаётся");
        assertEquals(0, ((Number) offerRow().get("reserved_portions")).intValue(), "порции вернулись в продажу");
        assertEquals(0, ((Number) offerRow().get("sold_portions")).intValue());
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'LATE_PAYMENT'", id));

        mvc.perform(get("/orders/" + id).with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Оплата пришла после окончания брони")))
                .andExpect(content().string(not(containsString("qr.png"))));
        mvc.perform(get("/orders/" + id + "/qr.png").with(as(customer))).andExpect(status().isNotFound());
    }

    @Test
    void latePaymentAfterExpiryJobAlreadyReleasedHold() throws Exception {
        UUID id = reserve(1);
        String provider = providerId(startPayment(id));
        advance(181);
        reservations.expireDue(); // фоновая задача Жаныбека успела снять бронь
        assertEquals("CANCELLED", order(id).get("status"));
        assertEquals(PaymentResult.LATE_PAYMENT, payments.complete(provider, true, "4242", null));
        assertEquals("LATE_PAYMENT", order(id).get("cancel_reason"));
        assertEquals("COMPLETED", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", id), "возврат один");
        assertEquals(0, ((Number) offerRow().get("reserved_portions")).intValue(), "бронь не снята дважды");
    }

    // ---------- Доступ ----------

    @Test
    void codeAndQrAreVisibleOnlyToOwner() throws Exception {
        UUID id = reserve(1);
        payByCard(startPayment(id), "4242424242424242");

        mvc.perform(get("/orders/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/" + id).with(as(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/orders/" + id).with(as(otherCustomer))).andExpect(status().isNotFound());
        mvc.perform(get("/orders/" + id + "/qr.png").with(as(otherCustomer))).andExpect(status().isNotFound());

        byte[] png = mvc.perform(get("/orders/" + id + "/qr.png").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", startsWith("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
    }

    @Test
    void otherBuyerCannotOpenOrPayForeignPayment() throws Exception {
        UUID id = reserve(1);
        String payUrl = startPayment(id);
        mvc.perform(get(payUrl).with(as(otherCustomer))).andExpect(status().isNotFound());
        mvc.perform(post(payUrl).with(as(otherCustomer)).with(csrf()).param("action", "pay")
                .param("cardNumber", "4242424242424242").param("expiry", "12/40").param("cvv", "123"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reservations/" + id + "/pay").with(as(otherCustomer)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(payUrl).with(as(customer)).param("action", "cancel")).andExpect(status().isForbidden()); // без CSRF
        assertEquals("CREATED", db.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
    }

    @Test
    void unpaidOrderPageLeadsBackToCheckout() throws Exception {
        UUID id = reserve(1);
        mvc.perform(get("/orders/" + id).with(as(customer))).andExpect(redirectedUrl("/reservations/" + id));
        mvc.perform(get("/orders/" + id + "/qr.png").with(as(customer))).andExpect(status().isNotFound());
    }
}
