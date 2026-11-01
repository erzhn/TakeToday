package com.example.taketoday.orderhistory;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Статус «Время получения истекло» ставится в базе фоновой задачей (п. 4.8.2 ТЗ, SPEC раздел 10). */
class PickupExpiryTests extends PaymentTestSupport {

    @Autowired PickupExpiryService expiry;
    @Autowired PaymentService payments;

    private UUID paidOrder() {
        UUID id = reserve(1);
        assertEquals(PaymentResult.PAID, payments.complete(provider(payments.start(id, customer)), true, "4242", null));
        return id;
    }

    private static String provider(String url) {
        return url.substring("/mock-pay/".length());
    }

    /** Часы — на минуту позже конца окна выдачи (набор: завтра 19:00–21:00). */
    private void afterPickupWindow() {
        advance(Duration.between(now, pickupStart.plusSeconds(7200)).toSeconds() + 60);
    }

    private String statusOf(UUID id) {
        return (String) order(id).get("status");
    }

    @Test
    void paidOrderNotPickedUpBecomesPickupExpired() throws Exception {
        UUID id = paidOrder();
        int soldBefore = ((Number) offerRow().get("sold_portions")).intValue();

        assertEquals(0, expiry.expireDue(), "Окно выдачи ещё не закончилось");
        assertEquals("PAID", statusOf(id));

        afterPickupWindow();
        assertEquals(1, expiry.expireDue());
        assertEquals("PICKUP_EXPIRED", statusOf(id));
        assertEquals(0, expiry.expireDue(), "Повторный проход ничего не меняет");

        // Порции остаются проданными, деньги не возвращаются
        assertEquals(soldBefore, ((Number) offerRow().get("sold_portions")).intValue());
        assertEquals("NONE", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'PICKUP_EXPIRED'", id));

        // Покупатель в «Моих заказах» и сотрудник на странице выдачи видят понятный статус
        mvc.perform(get("/orders").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Время получения истекло")));
        String code = (String) order(id).get("pickup_code");
        mvc.perform(post("/staff/pickup/check").with(as(staff)).with(csrf()).param("code", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Время получения истекло")));
    }

    @Test
    void issuedAndUnpaidOrdersAreNotTouched() {
        UUID issued = paidOrder();
        db.update("UPDATE reservation_orders SET status = 'ISSUED', issued_at = CURRENT_TIMESTAMP WHERE id = ?", issued);
        UUID unpaid = reserve(1);

        afterPickupWindow();
        expiry.expireDue();

        assertEquals("ISSUED", statusOf(issued));
        assertEquals("AWAITING_PAYMENT", statusOf(unpaid), "Неоплаченные брони снимает другая задача");
    }

    @Test
    void latePaymentForExpiredOrderIsRefundedWithoutCancellingIt() {
        UUID id = reserve(1);
        String first = provider(payments.start(id, customer));
        String second = provider(payments.start(id, customer));
        assertEquals(PaymentResult.PAID, payments.complete(second, true, "4242", null));
        afterPickupWindow();
        expiry.expireDue();

        // Банк всё же списал деньги по старой попытке — это повтор оплаты уже оплаченного заказа
        assertEquals(PaymentResult.ALREADY_PAID, payments.complete(first, true, "1111", null));
        assertEquals("PICKUP_EXPIRED", statusOf(id));
        assertNull(order(id).get("cancel_reason"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", id));
    }
}
