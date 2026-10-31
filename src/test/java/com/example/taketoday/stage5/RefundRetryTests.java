package com.example.taketoday.stage5;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.payment.PaymentUnavailableException;
import com.example.taketoday.payment.mock.MockPaymentGateway;
import com.example.taketoday.refund.RefundService;
import com.example.taketoday.refund.RefundStatus;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Платёжная служба не отвечает на возврат: 3 попытки, потом «Ошибка возврата» и ручной возврат администратором
 * (п. 4.6.4 «с участием администратора», 4.10.3, 5.3.1).
 */
class RefundRetryTests extends PaymentTestSupport {

    @MockitoSpyBean
    MockPaymentGateway gateway;

    @Autowired
    PaymentService payments;

    @Autowired
    RefundService refunds;

    @Test
    void failedRefundIsFinishedByAdmin() throws Exception {
        UUID id = reserve(1);
        String provider = payments.start(id, customer).substring("/mock-pay/".length());
        assertEquals(PaymentResult.PAID, payments.complete(provider, true, "4242", null));

        doThrow(new PaymentUnavailableException("нет связи")).when(gateway).refund(any(), anyInt(), any());
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()))
                .andExpect(flash().attribute("cancelMessage", containsString("Возврат денег оформлен")));
        UUID refund = db.queryForObject("SELECT id FROM refunds WHERE order_id = ?", UUID.class, id);
        assertEquals("PENDING", order(id).get("refund_status"));

        // Фоновая задача повторяет; после 3 попыток — ошибка, нужен администратор
        assertEquals(RefundStatus.PENDING, refunds.process(refund));
        assertEquals(RefundStatus.FAILED, refunds.process(refund));
        assertEquals(RefundStatus.FAILED, refunds.process(refund), "FAILED больше не трогаем");
        assertEquals(3, db.queryForObject("SELECT attempts FROM refunds WHERE id = ?", Integer.class, refund));
        assertEquals("FAILED", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'REFUND_FAILED'", id));

        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        // Кнопка в блоке «Нужен ручной возврат» (сама фраза есть ещё и в журнале событий)
        mvc.perform(get("/admin").with(as(admin))).andExpect(content().string(containsString("Вернуть вручную")));
        mvc.perform(get("/orders/" + id).with(as(customer)))
                .andExpect(content().string(containsString("Возврат задерживается")));

        // Служба снова работает — администратор оформляет возврат вручную
        Mockito.reset(gateway);
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("adminError", containsString("Укажите причину")));
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf())
                        .param("reason", "Автоматический возврат не прошёл"))
                .andExpect(redirectedUrl("/admin/orders/" + id))
                .andExpect(flash().attribute("success", "Деньги возвращены покупателю."));

        assertEquals("COMPLETED", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ? AND status = 'COMPLETED' "
                + "AND initiated_by = 'ADMIN' AND admin_id = ?", id, admin));
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Вернуть вручную"))));

        // Второй ручной возврат невозможен — деньги уже вернули
        mvc.perform(post("/admin/orders/" + id + "/refund").with(as(admin)).with(csrf()).param("reason", "Ещё раз"))
                .andExpect(flash().attribute("adminError", containsString("нечего возвращать")));
    }

    @Test
    void pendingRefundCanBeRetriedNow() throws Exception {
        UUID id = reserve(1);
        String provider = payments.start(id, customer).substring("/mock-pay/".length());
        payments.complete(provider, true, "4242", null);
        doThrow(new PaymentUnavailableException("нет связи")).when(gateway).refund(any(), anyInt(), any());
        mvc.perform(post("/orders/" + id + "/cancel").with(as(customer)).with(csrf()));
        Mockito.reset(gateway);

        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        mvc.perform(get("/admin/orders/" + id).with(as(admin))).andExpect(content().string(containsString("Повторить сейчас")));
        mvc.perform(post("/admin/orders/" + id + "/refund/retry").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "Деньги возвращены покупателю."));
        assertEquals("COMPLETED", order(id).get("refund_status"));
        assertEquals(1, count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", id), "тот же возврат, не второй");
    }
}
