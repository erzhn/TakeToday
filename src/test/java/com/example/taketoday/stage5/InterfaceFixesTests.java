package com.example.taketoday.stage5;

import com.example.taketoday.payment.PaymentResult;
import com.example.taketoday.payment.PaymentService;
import com.example.taketoday.stage3.PaymentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Исправления по замечаниям после проверки: кабинет заведения, «Как это работает», тексты. */
class InterfaceFixesTests extends PaymentTestSupport {

    @Autowired
    PaymentService payments;

    @Test
    void dashboardShowsOneSumAndPickupWindowOfWaitingOrders() throws Exception {
        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(model().attribute("nextWindow", (Object) null))
                .andExpect(content().string(containsString("Сейчас никто не ждёт выдачи")));

        UUID id = reserve(2);
        String provider = payments.start(id, customer).substring("/mock-pay/".length());
        assertEquals(PaymentResult.PAID, payments.complete(provider, true, "4242", null));

        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(status().isOk())
                // «Оплачено сегодня» — одна сумма, количество заказов — подписью
                .andExpect(content().string(containsString("<span>500</span> <small>сом</small>")))
                .andExpect(content().string(containsString("Заказов: <span>1</span>")))
                // Время у «Ожидают выдачи» — окно выдачи заказа, а не часы работы заведения
                .andExpect(model().attribute("nextWindow", "19:00–21:00"))
                .andExpect(content().string(containsString("Оплачены, покупатели скоро подойдут")));
    }

    @Test
    void howItWorksIsOwnPageWithActiveMenuItem() throws Exception {
        mvc.perform(get("/how")).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nav-link active\">Как это работает")))
                .andExpect(content().string(containsString("Отмена до начала выдачи")));
        mvc.perform(get("/how").with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Мои заказы")));
        mvc.perform(get("/catalog"))
                .andExpect(content().string(containsString("href=\"/how\"")))
                .andExpect(content().string(not(containsString("href=\"/#home\""))));
        mvc.perform(get("/index.html"))
                .andExpect(content().string(containsString("href=\"/how\"")));
    }

    @Test
    void homeTextsDoNotPromiseFutureStages() throws Exception {
        mvc.perform(get("/api/customer/home").with(as(customer)))
                .andExpect(content().string(not(containsString("будут доступны"))));
        mvc.perform(get("/app.js"))
                .andExpect(content().string(not(containsString("на следующем этапе"))));
    }
}
