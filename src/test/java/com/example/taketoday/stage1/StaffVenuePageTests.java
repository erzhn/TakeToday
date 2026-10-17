package com.example.taketoday.stage1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Сведения о заведении (п. 4.2.2 ТЗ): сотрудник меняет только своё заведение. */
class StaffVenuePageTests extends Stage1TestSupport {

    private UUID staff;
    private UUID otherStaff;

    @BeforeEach
    void createVenues() {
        staff = staffWithVenue("staff@example.com", "Пекарня «Тандыр»");
        otherStaff = staffWithVenue("other@example.com", "Кофейня «Утро»");
    }

    /**
     * Заполненная форма: все 7 дней рабочие 09:00–21:00.
     * overrides — пары «поле, значение», которые заменяют значения по умолчанию
     * (MockMvc при повторном param() добавляет второе значение, а не заменяет — поэтому так).
     */
    private MockMultipartHttpServletRequestBuilder validForm(String... overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "Пекарня «Тандыр» на Киевской");
        values.put("category", "BAKERY");
        values.put("address", "г. Бишкек, ул. Киевская, 95");
        values.put("district", "PERVOMAISKY");
        values.put("phone", "+996555341289");
        values.put("contactEmail", "Tandyr@Mail.kg");
        values.put("instagram", "https://instagram.com/tandyr_bakery");
        for (int i = 0; i < 7; i++) {
            values.put("workDays[" + i + "].dayOfWeek", String.valueOf(i + 1));
            values.put("workDays[" + i + "].working", "true");
            values.put("workDays[" + i + "].openTime", "09:00");
            values.put("workDays[" + i + "].closeTime", "21:00");
        }
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            values.put(overrides[i], overrides[i + 1]);
        }

        MockMultipartHttpServletRequestBuilder request = multipart("/staff/venue");
        request.with(as(staff)).with(csrf());
        values.forEach((name, value) -> request.param(name, value));
        return request;
    }

    @Test
    void staffSeesOwnVenue() throws Exception {
        mvc.perform(get("/staff/venue").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Пекарня «Тандыр»")))
                .andExpect(content().string(containsString("г. Бишкек, ул. Токтогула, 1")));
    }

    @Test
    void staffHomeShowsVenueCard() throws Exception {
        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Пекарня «Тандыр»")));
    }

    @Test
    void staffSavesVenueAndSchedule() throws Exception {
        mvc.perform(validForm(
                        "workDays[5].working", "false",
                        "workDays[6].working", "false"))
                .andExpect(redirectedUrl("/staff/venue"));

        Map<String, Object> venue = venueOf(staff);
        assertThat(venue.get("name")).isEqualTo("Пекарня «Тандыр» на Киевской");
        assertThat(venue.get("category")).isEqualTo("BAKERY");
        assertThat(venue.get("address")).isEqualTo("г. Бишкек, ул. Киевская, 95");
        assertThat(venue.get("contact")).isEqualTo("+996 555 341 289");
        assertThat(venue.get("contact_email")).isEqualTo("tandyr@mail.kg");
        assertThat(venue.get("instagram")).isEqualTo("@tandyr_bakery");
        assertThat(venue.get("working_hours")).isEqualTo("Пн–Пт 09:00–21:00, Сб–Вс выходной");
        assertThat(venue.get("updated_at")).isNotNull();

        UUID venueId = (UUID) venue.get("id");
        assertThat(db.queryForObject(
                "SELECT COUNT(*) FROM establishment_work_days WHERE establishment_id = ?", Integer.class, venueId))
                .isEqualTo(7);
        assertThat(db.queryForObject(
                "SELECT open_time FROM establishment_work_days WHERE establishment_id = ? AND day_of_week = 1",
                LocalTime.class, venueId)).isEqualTo(LocalTime.of(9, 0));
        assertThat(db.queryForObject(
                "SELECT working FROM establishment_work_days WHERE establishment_id = ? AND day_of_week = 7",
                Boolean.class, venueId)).isFalse();
    }

    @Test
    void staffChangesOnlyOwnVenue() throws Exception {
        mvc.perform(validForm()).andExpect(redirectedUrl("/staff/venue"));

        assertThat(venueOf(otherStaff).get("name")).isEqualTo("Кофейня «Утро»");
    }

    @Test
    void closeBeforeOpenIsRejected() throws Exception {
        mvc.perform(validForm(
                        "workDays[0].openTime", "21:00",
                        "workDays[0].closeTime", "09:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Время закрытия раньше времени открытия")));
    }

    @Test
    void addressIsRequired() throws Exception {
        mvc.perform(validForm("address", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Укажите адрес")));

        assertThat(venueOf(staff).get("address")).isEqualTo("г. Бишкек, ул. Токтогула, 1");
    }

    @Test
    void allDaysOffIsRejected() throws Exception {
        String[] allOff = new String[14];
        for (int i = 0; i < 7; i++) {
            allOff[i * 2] = "workDays[" + i + "].working";
            allOff[i * 2 + 1] = "false";
        }
        mvc.perform(validForm(allOff))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Укажите хотя бы один рабочий день")));
    }

    @Test
    void logoMustBeImage() throws Exception {
        var request = validForm();
        request.file(new MockMultipartFile("logo", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3}));
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Только JPG или PNG")));

        assertThat(venueOf(staff).get("logo_path")).isNull();
    }

    @Test
    void staffWithoutVenueGetsForbidden() throws Exception {
        UUID lonely = account("novenue@example.com", "Без заведения", "STAFF");
        mvc.perform(get("/staff/venue").with(as(lonely))).andExpect(status().isForbidden());
    }

    @Test
    void emailContactFromRegistrationGoesToEmailField() throws Exception {
        db.update("UPDATE establishments SET contact = ? WHERE owner_id = ?", "tandyr@mail.kg", staff);

        mvc.perform(get("/staff/venue").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"tandyr@mail.kg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Телефон в формате"))));

        // Сохранить можно без телефона — тогда в contact попадает почта
        mvc.perform(validForm("phone", "", "contactEmail", "tandyr@mail.kg"))
                .andExpect(redirectedUrl("/staff/venue"));
        assertThat(venueOf(staff).get("contact")).isEqualTo("tandyr@mail.kg");
    }

    @Test
    void phoneOrEmailIsRequired() throws Exception {
        mvc.perform(validForm("phone", "", "contactEmail", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Укажите телефон или почту для связи")));
    }

    @Test
    void registrationHoursShownUntilScheduleSaved() throws Exception {
        db.update("UPDATE establishments SET working_hours = ? WHERE owner_id = ?", "Пн–Сб, 10:00–20:00", staff);

        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(content().string(containsString("График: Пн–Сб, 10:00–20:00")));
        mvc.perform(get("/staff/venue").with(as(staff)))
                .andExpect(content().string(containsString("При регистрации указано")));

        mvc.perform(validForm()).andExpect(redirectedUrl("/staff/venue"));

        mvc.perform(get("/staff").with(as(staff)))
                .andExpect(content().string(containsString("График: ежедневно 09:00–21:00")));
        mvc.perform(get("/staff/venue").with(as(staff)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("При регистрации указано"))));
    }
}
