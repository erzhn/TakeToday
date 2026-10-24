package com.example.taketoday.stage3;

import com.example.taketoday.common.AppTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Просьба Жаныбека: то, что выкладывает продавец через кабинет (этап 2, Эржан), должно нормально показываться
 * покупателю в каталоге (Жаныбек) и бронироваться и оплачиваться (этапы 3).
 */
class SellerOfferToBuyerTests extends PaymentTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Test
    void offerPublishedBySellerIsShownBookedAndPaid() throws Exception {
        String tomorrow = LocalDate.now(AppTime.ZONE).plusDays(1).toString();
        mvc.perform(multipart("/staff/offers/new")
                        .file(new MockMultipartFile("photo", "box.png", "image/png", PNG))
                        .param("title", "Вечерний бокс самсы")
                        .param("foodType", "BAKERY")
                        .param("composition", "3 самсы с говядиной, лепёшка")
                        .param("regularPrice", "600")
                        .param("discountPrice", "300")
                        .param("totalPortions", "4")
                        .param("pickupDate", tomorrow)
                        .param("pickupStart", "19:00")
                        .param("pickupEnd", "21:00")
                        .with(as(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Map<String, Object> created = db.queryForMap("SELECT * FROM offers WHERE title = 'Вечерний бокс самсы'");
        UUID id = (UUID) created.get("id");
        String photo = (String) created.get("photo_path");
        assertTrue(photo.startsWith("/uploads/offers/"), photo);

        // Каталог и карточка набора у покупателя
        mvc.perform(get("/catalog").with(as(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Вечерний бокс самсы")))
                .andExpect(content().string(containsString(photo)))
                .andExpect(content().string(containsString("300")));
        mvc.perform(get("/offers/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("3 самсы с говядиной, лепёшка")))
                .andExpect(content().string(containsString("Пекарня «Тандыр»")))
                .andExpect(content().string(containsString("г. Бишкек, ул. Токтогула, 1")))
                .andExpect(content().string(containsString(photo)))
                .andExpect(content().string(containsString("19:00")));
        // Фото продавца реально отдаётся
        mvc.perform(get(photo)).andExpect(status().isOk());

        // Бронь двух порций, как это делает кнопка на карточке
        String body = "{\"offerId\":\"" + id + "\",\"requestKey\":\"" + UUID.randomUUID() + "\",\"quantity\":2}";
        String json = mvc.perform(post("/api/orders/reservations").with(as(customer)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.amount").value(600))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(json.replaceAll(".*\"order\":\\{\"id\":\"([^\"]+)\".*", "$1"));

        String payUrl = mvc.perform(post("/reservations/" + orderId + "/pay").with(as(customer)).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post(payUrl).with(as(customer)).with(csrf()).param("action", "pay")
                .param("cardNumber", "4242 4242 4242 4242").param("expiry", "12/40").param("cvv", "123"));

        assertEquals("PAID", order(orderId).get("status"));
        Map<String, Object> after = db.queryForMap("SELECT * FROM offers WHERE id = ?", id);
        assertEquals(0, ((Number) after.get("reserved_portions")).intValue());
        assertEquals(2, ((Number) after.get("sold_portions")).intValue());
        // Покупателю осталось 2 порции, продавец видит продажу в кабинете
        mvc.perform(get("/offers/" + id)).andExpect(content().string(containsString("Доступных порций: <strong>2</strong>")));
        mvc.perform(get("/orders/" + orderId).with(as(customer)))
                .andExpect(content().string(containsString("Вечерний бокс самсы")))
                .andExpect(content().string(containsString(photo)))
                .andExpect(content().string(containsString("Экономия: <span>600 сом</span>")));
    }
}
