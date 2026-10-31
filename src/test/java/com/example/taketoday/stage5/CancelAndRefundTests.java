package com.example.taketoday.stage5;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Этап 5 (Эржан): отмена заказа покупателем и заведением, автоматический возврат денег (п. 4.6.4, 4.6.5, 4.9.3 ТЗ). */
class CancelAndRefundTests extends PaymentTestSupport {

    @Autowired
    PaymentService payments;

    private String provider(UUID orderId) {
        return payments.start(orderId, customer).substring("/mock-pay/".length());
    }

    private UUID paidOrder(int quantity) {
        UUID id = reserve(quantity);
        assertEquals(PaymentResult.PAID, payments.complete(provider(id), true, "4242", null));
        return id;
    }

    private int offerInt(String column) {
        return ((Number) offerRow().get(column)).intValue();
    }

    // ---------- Покупатель ----------

    @Test
    void customerCancelsPaidOrderAndGetsMoneyBack() throws Exception {
        UUID id = paidOrder(2);
        String code = (String) order(id).get("pickup_code");
        assertEquals(2, offerInt("sold_portions"));

        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + id))
                .andExpect(flash().attribute("cancelMessage", containsString("Деньги вернулись на карту")));

        Map<String, Object> row = order(id);
        assertEquals("CANCELLED", row.get("status"));
        assertEquals("CUSTOMER", row.get("cancel_reason"));
        assertEquals("COMPLETED", row.get("refund_status"));
        assertEquals(customer, row.get("cancelled_by"));
        assertNotNull(row.get("cancelled_at"));
        assertEquals(0, offerInt("sold_portions"), "порции вернулись в продажу");
        assertEquals(5, offerInt("total_portions"));

        Map<String, Object> refund = db.queryForMap("SELECT * FROM refunds WHERE order_id = ?", id);
        assertEquals("COMPLETED", refund.get("status"));
        assertEquals("AUTO", refund.get("initiated_by"));
        assertEquals(500, ((Number) refund.get("amount")).intValue());
        assertTrue(((String) refund.get("provider_refund_id")).startsWith("mockrefund_"));
        for (String event : new String[]{"ORDER_CANCELLED", "REFUND_REQUESTED", "REFUND_COMPLETED"}) {
            assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = ?", id, event), event);
        }

        // Покупатель видит отмену и возврат; QR больше нет; на кассе код не примут
        mvc.perform(get("/orders/" + id).with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы отменили заказ")))
                .andExpect(content().string(containsString("Деньги возвращены")))
                .andExpect(content().string(containsString("•••• <span>4242</span>")));
        mvc.perform(get("/orders/" + id + "/qr.png").with(as(customer))).andExpect(status().isNotFound());
        mvc.perform(get("/reservations/" + id).with(as(customer))).andExpect(redirectedUrl("/orders/" + id));
        String pickup = mvc.perform(post("/staff/pickup/check").with(as(staff)).with(csrf()).param("code", code))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pickup.contains("Заказ отменен"));
    }

    @Test
    void cancelIsPossibleOnlyBeforePickupStarts() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(content().string(containsString("Да, отменить и вернуть деньги")));

        advance(Duration.between(now, pickupStart).getSeconds() + 60); // выдача уже идёт
        mvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(content().string(containsString("отменить заказ нельзя")));
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(flash().attribute("cancelError", containsString("только до начала выдачи (19:00)")));
        assertEquals("PAID", order(id).get("status"));
        assertEquals(0, count("SELECT COUNT(*) FROM refunds"));
    }

    @Test
    void repeatedCancelDoesNotRefundTwice() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()));
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(flash().attribute("cancelError", "Заказ уже отменён."));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", id));
        assertEquals(0, offerInt("sold_portions"));
    }

    @Test
    void otherCustomerCannotCancel() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/orders/" + id + "/cancel").with(as(otherCustomer)).with(csrf()))
                .andExpect(flash().attribute("cancelError", "Заказ не найден."));
        mvc.perform(post("/orders/" + id + "/cancel").with(as(staff)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer))).andExpect(status().isForbidden()); // без CSRF
        assertEquals("PAID", order(id).get("status"));
    }

    @Test
    void customerReleasesReservationAndLatePaymentIsRefunded() throws Exception {
        UUID id = reserve(2);
        String provider = provider(id);          // страница оплаты открыта
        mvc.perform(post("/reservations/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + id))
                .andExpect(flash().attribute("cancelMessage", containsString("Бронь отменена")));
        assertEquals("CANCELLED", order(id).get("status"));
        assertEquals("CUSTOMER", order(id).get("cancel_reason"));
        assertEquals(0, offerInt("reserved_portions"), "порции сразу вернулись в продажу");
        assertEquals("CANCELLED", db.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
        mvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(content().string(containsString("Вы отменили бронь")))
                .andExpect(content().string(containsString("деньги не списывались")));

        // Банк всё же подтвердил оплату со старой страницы — деньги возвращаются, причина отмены не меняется
        assertEquals(PaymentResult.LATE_PAYMENT, payments.complete(provider, true, "4242", null));
        assertEquals("CUSTOMER", order(id).get("cancel_reason"));
        assertEquals("COMPLETED", order(id).get("refund_status"));
        assertEquals(0, offerInt("sold_portions"));
    }

    // ---------- Заведение ----------

    @Test
    void venueThatCannotGiveOrderCancelsWithRefund() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/staff/pickup/" + id + "/cancel").with(as(staff)).with(csrf()))
                .andExpect(flash().attribute("pickupError", containsString("Выберите причину")));
        assertEquals("PAID", order(id).get("status"));

        UUID otherStaff = staffWithVenue("cafe@example.com", "Кофейня «Утро»");
        mvc.perform(post("/staff/pickup/" + id + "/cancel").with(as(otherStaff)).with(csrf()).param("reason", "Набор закончился"))
                .andExpect(flash().attribute("pickupError", "Заказ не найден в вашем заведении"));

        mvc.perform(post("/staff/pickup/" + id + "/cancel").with(as(staff)).with(csrf()).param("reason", "Набор закончился"))
                .andExpect(redirectedUrl("/staff/pickup"))
                .andExpect(flash().attribute("success", "Заказ отменён"));
        Map<String, Object> row = order(id);
        assertEquals("CANCELLED", row.get("status"));
        assertEquals("VENUE", row.get("cancel_reason"));
        assertEquals("Набор закончился", row.get("cancel_comment"));
        assertEquals("COMPLETED", row.get("refund_status"));
        assertEquals(0, offerInt("sold_portions"));
        assertEquals(4, offerInt("total_portions"), "порцию не вернули в продажу — её нет");

        mvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(content().string(containsString("Заведение не может выдать заказ")))
                .andExpect(content().string(containsString("Набор закончился")));
    }

    @Test
    void customerCannotCancelIssuedOrder() throws Exception {
        UUID id = paidOrder(1);
        mvc.perform(post("/staff/pickup/" + id + "/confirm").with(as(staff)).with(csrf()));
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(flash().attribute("cancelError", containsString("уже выдан")));
        assertEquals("ISSUED", order(id).get("status"));
    }
}
