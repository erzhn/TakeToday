package com.example.taketoday.stage2;

import com.example.taketoday.common.AppTime;
import com.example.taketoday.stage1.Stage1TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Этап 2 (Эржан): размещение, изменение и снятие предложений (п. 4.4.1–4.4.3, 5.2.3, 5.5.3 ТЗ). */
class StaffOfferPageTests extends Stage1TestSupport {

    /** Начало настоящего PNG — сервер проверяет первые байты, а не только тип файла из браузера. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    private UUID staff;
    private UUID otherStaff;
    private UUID venue;
    private UUID otherVenue;

    @BeforeEach
    void createVenues() {
        staff = staffWithVenue("staff@example.com", "Пекарня «Тандыр»");
        otherStaff = staffWithVenue("other@example.com", "Кофейня «Утро»");
        venue = (UUID) venueOf(staff).get("id");
        otherVenue = (UUID) venueOf(otherStaff).get("id");
    }

    private static LocalDate tomorrow() {
        return LocalDate.now(AppTime.ZONE).plusDays(1);
    }

    /** Заполненная форма: выдача завтра 19:00–21:00. overrides заменяют значения («поле», «значение»). */
    private MockMultipartHttpServletRequestBuilder offerForm(String url, UUID who, boolean withPhoto, String... overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", "Вечерний бокс выпечки");
        values.put("foodType", "BAKERY");
        values.put("composition", "2 самсы, 3 булочки с изюмом, лепёшка");
        values.put("regularPrice", "500");
        values.put("discountPrice", "250");
        values.put("totalPortions", "5");
        values.put("pickupDate", tomorrow().toString());
        values.put("pickupStart", "19:00");
        values.put("pickupEnd", "21:00");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            values.put(overrides[i], overrides[i + 1]);
        }
        MockMultipartHttpServletRequestBuilder request = multipart(url);
        if (withPhoto) {
            request.file(new MockMultipartFile("photo", "box.png", "image/png", PNG));
        }
        request.with(as(who)).with(csrf());
        values.forEach(request::param);
        return request;
    }

    /** Предложение прямо в базе. */
    private UUID insertOffer(UUID establishment, String title, int total, int reserved, int sold,
                             Instant start, Instant end, String status) {
        UUID id = UUID.randomUUID();
        db.update("""
                        INSERT INTO offers (id, establishment_id, title, composition, food_type, photo_path, regular_price,
                                            discount_price, total_portions, reserved_portions, sold_portions,
                                            pickup_start, pickup_end, status)
                        VALUES (?, ?, ?, 'Состав набора для теста', 'BAKERY', '/images/offers/bakery.jpg', 500, 250, ?, ?, ?, ?, ?, ?)
                        """,
                id, establishment, title, total, reserved, sold, Timestamp.from(start), Timestamp.from(end), status);
        return id;
    }

    private UUID activeOffer(UUID establishment, String title) {
        Instant start = tomorrow().atTime(19, 0).atZone(AppTime.ZONE).toInstant();
        return insertOffer(establishment, title, 5, 0, 0, start, start.plusSeconds(7200), "ACTIVE");
    }

    private Map<String, Object> offerRow(UUID id) {
        return db.queryForMap("SELECT * FROM offers WHERE id = ?", id);
    }

    // ---------- Создание (п. 4.4.1) ----------

    @Test
    void staffCreatesOfferAndItAppearsInCatalog() throws Exception {
        mvc.perform(offerForm("/staff/offers/new", staff, true))
                .andExpect(redirectedUrl("/staff#offers"))
                .andExpect(flash().attribute("success", "Предложение опубликовано"));

        Map<String, Object> row = db.queryForMap("SELECT * FROM offers WHERE establishment_id = ?", venue);
        assertThat(row.get("title")).isEqualTo("Вечерний бокс выпечки");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat((String) row.get("photo_path")).startsWith("/uploads/offers/").endsWith(".png");
        assertThat(((Number) row.get("discount_price")).intValue()).isEqualTo(250);
        Instant start = db.queryForObject("SELECT pickup_start FROM offers WHERE id = ?", Timestamp.class, row.get("id")).toInstant();
        assertThat(start).isEqualTo(tomorrow().atTime(19, 0).atZone(AppTime.ZONE).toInstant());

        // Каталог Жаныбека показывает новый набор
        mvc.perform(get("/catalog")).andExpect(content().string(containsString("Вечерний бокс выпечки")));
        // и он в таблице кабинета
        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(content().string(containsString("Вечерний бокс выпечки")))
                .andExpect(content().string(containsString("−50% скидка")));
    }

    @Test
    void photoIsRequired() throws Exception {
        mvc.perform(offerForm("/staff/offers/new", staff, false))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Добавьте фото")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM offers", Integer.class)).isZero();
    }

    @Test
    void fakeImageIsRejected() throws Exception {
        var request = offerForm("/staff/offers/new", staff, false);
        request.file(new MockMultipartFile("photo", "virus.png", "image/png", "<script>alert(1)</script>".getBytes()));
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Только JPG или PNG")));
    }

    @Test
    void validationMessagesFromTheSpec() throws Exception {
        mvc.perform(offerForm("/staff/offers/new", staff, true, "regularPrice", ""))
                .andExpect(content().string(containsString("Укажите цену")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "discountPrice", "600"))
                .andExpect(content().string(containsString("Цена со скидкой должна быть меньше обычной")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "totalPortions", "-2"))
                .andExpect(content().string(containsString("Количество порций не может быть отрицательным")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "totalPortions", "0"))
                .andExpect(content().string(containsString("Укажите хотя бы 1 порцию")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "pickupStart", "21:00", "pickupEnd", "19:00"))
                .andExpect(content().string(containsString("Время окончания выдачи раньше времени начала")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "pickupDate", LocalDate.now(AppTime.ZONE).minusDays(1).toString()))
                .andExpect(content().string(containsString("Время окончания выдачи уже прошло")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "foodType", ""))
                .andExpect(content().string(containsString("Выберите вид еды")));
        mvc.perform(offerForm("/staff/offers/new", staff, true, "regularPrice", "abc"))
                .andExpect(content().string(containsString("Введите целое число")));

        assertThat(db.queryForObject("SELECT COUNT(*) FROM offers", Integer.class)).isZero();
    }

    // ---------- Изменение (п. 4.4.2) ----------

    @Test
    void staffEditsOwnOffer() throws Exception {
        UUID id = activeOffer(venue, "Старое название");
        mvc.perform(get("/staff/offers/{id}/edit", id).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Старое название")));

        mvc.perform(offerForm("/staff/offers/" + id + "/edit", staff, false,
                        "title", "Новое название", "totalPortions", "8"))
                .andExpect(redirectedUrl("/staff#offers"));

        Map<String, Object> row = offerRow(id);
        assertThat(row.get("title")).isEqualTo("Новое название");
        assertThat(((Number) row.get("total_portions")).intValue()).isEqualTo(8);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1);
        assertThat(row.get("photo_path")).isEqualTo("/images/offers/bakery.jpg");
    }

    @Test
    void portionsCannotGoBelowReservedAndSold() throws Exception {
        Instant start = tomorrow().atTime(19, 0).atZone(AppTime.ZONE).toInstant();
        UUID id = insertOffer(venue, "Почти раскупили", 6, 1, 3, start, start.plusSeconds(3600), "ACTIVE");

        mvc.perform(offerForm("/staff/offers/" + id + "/edit", staff, false, "totalPortions", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Нельзя сделать порций меньше, чем уже забронировано и оплачено (4)")));
        assertThat(((Number) offerRow(id).get("total_portions")).intValue()).isEqualTo(6);

        mvc.perform(offerForm("/staff/offers/" + id + "/edit", staff, false, "totalPortions", "4"))
                .andExpect(redirectedUrl("/staff#offers"));
    }

    // ---------- Снять с продажи / вернуть (п. 4.4.3) ----------

    @Test
    void staffWithdrawsAndResumesOffer() throws Exception {
        UUID id = activeOffer(venue, "Набор");
        mvc.perform(post("/staff/offers/{id}/withdraw", id).with(as(staff)).with(csrf()))
                .andExpect(redirectedUrl("/staff#offers"));
        assertThat(offerRow(id).get("status")).isEqualTo("WITHDRAWN");
        mvc.perform(get("/catalog")).andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Набор<"))));

        mvc.perform(post("/staff/offers/{id}/resume", id).with(as(staff)).with(csrf()))
                .andExpect(redirectedUrl("/staff#offers"));
        assertThat(offerRow(id).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void expiredOfferCannotBeResumedButCanBeRepeated() throws Exception {
        Instant start = Instant.now().minusSeconds(7200);
        UUID id = insertOffer(venue, "Вчерашний набор", 5, 0, 0, start, start.plusSeconds(3600), "WITHDRAWN");

        mvc.perform(post("/staff/offers/{id}/resume", id).with(as(staff)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("время выдачи уже прошло")));
        assertThat(offerRow(id).get("status")).isEqualTo("WITHDRAWN");

        mvc.perform(get("/staff/offers/new").param("from", id.toString()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вчерашний набор")));
        // «Повторить» без нового фото — берётся фото прошлого набора
        mvc.perform(offerForm("/staff/offers/new", staff, false, "title", "Вчерашний набор", "copyFrom", id.toString()))
                .andExpect(redirectedUrl("/staff#offers"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM offers WHERE photo_path = '/images/offers/bakery.jpg'", Integer.class))
                .isEqualTo(2);
        // Этап 5: старое предложение уходит в архив — в списке нет второй строки с «Повторить»
        assertThat(offerRow(id).get("archived")).isEqualTo(true);
        String page = mvc.perform(get("/staff").with(as(staff))).andReturn().getResponse().getContentAsString();
        assertThat(page.split("Вчерашний набор", -1).length - 1).isEqualTo(1);
    }

    // ---------- Только своё (п. 5.2.3) ----------

    @Test
    void staffCannotTouchAnotherVenuesOffer() throws Exception {
        UUID foreign = activeOffer(otherVenue, "Чужой набор");

        mvc.perform(get("/staff/offers/{id}/edit", foreign).with(as(staff))).andExpect(status().isNotFound());
        mvc.perform(offerForm("/staff/offers/" + foreign + "/edit", staff, false, "title", "Взлом"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/staff/offers/{id}/withdraw", foreign).with(as(staff)).with(csrf()))
                .andExpect(status().isNotFound());
        // «Повторить» чужое тоже нельзя
        mvc.perform(get("/staff/offers/new").param("from", foreign.toString()).with(as(staff)))
                .andExpect(status().isNotFound());

        Map<String, Object> row = offerRow(foreign);
        assertThat(row.get("title")).isEqualTo("Чужой набор");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        mvc.perform(get("/staff").with(as(staff))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Чужой набор"))));
    }

    @Test
    void buyerAndGuestCannotManageOffers() throws Exception {
        UUID buyer = account("buyer@example.com", "Айбек", "CUSTOMER");
        mvc.perform(get("/staff/offers/new").with(as(buyer))).andExpect(status().isForbidden());
        mvc.perform(offerForm("/staff/offers/new", buyer, true)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/offers/new")).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM offers", Integer.class)).isZero();
    }

    @Test
    void formNeedsCsrf() throws Exception {
        UUID id = activeOffer(venue, "Набор");
        mvc.perform(post("/staff/offers/{id}/withdraw", id).with(as(staff))).andExpect(status().isForbidden());
        assertThat(offerRow(id).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void reservedPortionsFromBookingBlockReducingTotal() throws Exception {
        // Бронь покупателя (Жаныбек) увеличивает reserved_portions и version — изменение сотрудником всё равно проходит,
        // если порций остаётся не меньше, чем забронировано
        UUID id = activeOffer(venue, "Набор");
        db.update("UPDATE offers SET reserved_portions = 2, version = version + 1 WHERE id = ?", id);
        mvc.perform(offerForm("/staff/offers/" + id + "/edit", staff, false, "title", "Новое", "totalPortions", "2"))
                .andExpect(redirectedUrl("/staff#offers"));
        assertThat(offerRow(id).get("title")).isEqualTo("Новое");
        mvc.perform(offerForm("/staff/offers/" + id + "/edit", staff, false, "totalPortions", "1"))
                .andExpect(content().string(containsString("Нельзя сделать порций меньше, чем уже забронировано и оплачено (2)")));
    }

    @Test
    void homeShowsCountsAndFilters() throws Exception {
        activeOffer(venue, "Активный набор");
        Instant past = Instant.now().minusSeconds(7200);
        insertOffer(venue, "Прошедший набор", 5, 0, 0, past, past.plusSeconds(3600), "ACTIVE");
        Instant start = tomorrow().atTime(LocalTime.of(10, 0)).atZone(AppTime.ZONE).toInstant();
        insertOffer(venue, "Снятый набор", 5, 0, 0, start, start.plusSeconds(3600), "WITHDRAWN");

        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Все (<span>3</span>)")))
                .andExpect(content().string(containsString("Активно (<span>1</span>)")))
                .andExpect(content().string(containsString("Снято (<span>1</span>)")))
                .andExpect(content().string(containsString("Время вышло (<span>1</span>)")))
                .andExpect(content().string(containsString("Повторить")))
                .andExpect(content().string(containsString("Вернуть")));
    }
}
