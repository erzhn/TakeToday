package com.example.taketoday.stage3;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Платёжная служба не отвечает: бронь сохраняется, покупатель видит понятное сообщение (п. 5.3.2 ТЗ). */
@TestPropertySource(properties = "app.payment.mock.available=false")
class PaymentUnavailableTests extends PaymentTestSupport {

    @Test
    void unavailableServiceKeepsHoldAndShowsMessage() throws Exception {
        UUID id = reserve(1);
        var result = mvc.perform(post("/reservations/" + id + "/pay").with(as(customer)).with(csrf()))
                .andExpect(redirectedUrl("/reservations/" + id))
                .andExpect(flash().attribute("paymentError", containsString("Платёжная служба временно недоступна")))
                .andReturn();
        assertEquals("AWAITING_PAYMENT", order(id).get("status"));
        assertEquals(1, ((Number) offerRow().get("reserved_portions")).intValue());
        assertEquals(0, count("SELECT COUNT(*) FROM payments"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND event_type = 'PAYMENT_UNAVAILABLE'", id));

        mvc.perform(get("/reservations/" + id).with(as(customer))
                        .flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("Платёжная служба временно недоступна")))
                .andExpect(content().string(containsString("Попробовать оплатить ещё раз")));
    }
}
