package com.example.taketoday.stage1;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Доступ к разделам по ролям (п. 4.2.3, 5.2.3 ТЗ). */
class AccessByRoleTests extends Stage1TestSupport {

    @Test
    void guestIsAskedToLogIn() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/staff/venue")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotOpenStaffOrAdmin() throws Exception {
        UUID buyer = account("buyer@example.com", "Айбек", "CUSTOMER");
        mvc.perform(get("/staff").with(as(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/venue").with(as(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(as(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/profile").with(as(buyer))).andExpect(status().isOk());
    }

    @Test
    void staffCannotOpenAdmin() throws Exception {
        UUID staff = staffWithVenue("staff@example.com", "Пекарня");
        mvc.perform(get("/admin").with(as(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/staff").with(as(staff))).andExpect(status().isOk());
        mvc.perform(get("/profile").with(as(staff))).andExpect(status().isOk());
    }

    @Test
    void adminSeesOverviewButNotStaffCabinet() throws Exception {
        UUID admin = account("admin@example.com", "Администратор", "ADMIN");
        account("buyer@example.com", "Айбек", "CUSTOMER");
        mvc.perform(get("/admin").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Всего пользователей")));
        mvc.perform(get("/staff").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void blockedAccountLosesAccess() throws Exception {
        UUID buyer = account("buyer@example.com", "Айбек", "CUSTOMER");
        var session = as(buyer);
        db.update("UPDATE accounts SET blocked = TRUE WHERE id = ?", buyer);
        mvc.perform(get("/profile").with(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void stylesAreOpenForEveryone() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }

    @Test
    void allSectionsFromMenuOpen() throws Exception {
        // Все разделы из меню уже сделаны (этапы 4–5), заглушек «Раздел появится» не осталось
        UUID buyer = account("buyer@example.com", "Айбек", "CUSTOMER");
        UUID staff = staffWithVenue("staff@example.com", "Пекарня");
        mvc.perform(get("/orders").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Мои заказы")));
        mvc.perform(get("/staff/pickup").with(as(staff))).andExpect(status().isOk());
        mvc.perform(get("/staff/orders").with(as(staff))).andExpect(status().isOk());
        mvc.perform(get("/staff/pickup").with(as(buyer))).andExpect(status().isForbidden());
    }

    @Test
    void fontsAreServedFromTheProject() throws Exception {
        mvc.perform(get("/fonts/material-symbols-outlined.woff2")).andExpect(status().isOk());
        mvc.perform(get("/css/fonts.css")).andExpect(status().isOk());
        UUID buyer = account("buyer@example.com", "Айбек", "CUSTOMER");
        mvc.perform(get("/profile").with(as(buyer)))
                .andExpect(content().string(containsString("/css/fonts.css")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("fonts.googleapis.com"))));
    }
}
