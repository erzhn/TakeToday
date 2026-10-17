package com.example.taketoday.stage1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Личный кабинет: просмотр и изменение данных, смена пароля (п. 4.2.1 ТЗ). */
class ProfilePageTests extends Stage1TestSupport {

    private static final String EMAIL = "buyer@example.com";
    private UUID buyer;

    @BeforeEach
    void createBuyer() {
        buyer = account(EMAIL, "Айбек Джумабаев", "CUSTOMER");
    }

    @Test
    void guestCannotOpenProfile() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void buyerSeesOwnData() throws Exception {
        mvc.perform(get("/profile").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Айбек Джумабаев")))
                .andExpect(content().string(containsString(EMAIL)))
                .andExpect(content().string(containsString("Покупатель")));
    }

    @Test
    void buyerUpdatesNameAndPhone() throws Exception {
        mvc.perform(post("/profile").with(as(buyer)).with(csrf())
                        .param("name", "  Айбек   Асанов ")
                        .param("phone", "+996700123456"))
                .andExpect(redirectedUrl("/profile"));

        assertThat(accountRow(buyer).get("name")).isEqualTo("Айбек Асанов");
        assertThat(accountRow(buyer).get("phone")).isEqualTo("+996 700 123 456");
    }

    @Test
    void wrongPhoneIsRejected() throws Exception {
        mvc.perform(post("/profile").with(as(buyer)).with(csrf())
                        .param("name", "Айбек")
                        .param("phone", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Телефон в формате +996 700 123 456")));

        assertThat(accountRow(buyer).get("phone")).isNull();
    }

    @Test
    void emptyNameIsRejected() throws Exception {
        mvc.perform(post("/profile").with(as(buyer)).with(csrf()).param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Введите имя")));

        assertThat(accountRow(buyer).get("name")).isEqualTo("Айбек Джумабаев");
    }

    @Test
    void profileFormNeedsCsrf() throws Exception {
        mvc.perform(post("/profile").with(as(buyer)).param("name", "Взлом"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        mvc.perform(post("/profile/password").with(as(buyer)).with(csrf())
                        .param("currentPassword", "NotMyPassword1")
                        .param("newPassword", "NewPassword456")
                        .param("confirmPassword", "NewPassword456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Текущий пароль указан неверно")));

        assertThat(encoder.matches(PASSWORD, (String) accountRow(buyer).get("password_hash"))).isTrue();
    }

    @Test
    void mismatchedPasswordsAreRejected() throws Exception {
        mvc.perform(post("/profile/password").with(as(buyer)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "NewPassword456")
                        .param("confirmPassword", "NewPassword457"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Пароли не совпадают")));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/profile/password").with(as(buyer)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "short1")
                        .param("confirmPassword", "short1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("от 10 символов")));
    }

    @Test
    void passwordChangeRevokesOtherSessions() throws Exception {
        var oldSession = as(buyer);

        mvc.perform(post("/profile/password").with(as(buyer)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "NewPassword456")
                        .param("confirmPassword", "NewPassword456"))
                .andExpect(redirectedUrl("/profile#password"));

        String hash = (String) accountRow(buyer).get("password_hash");
        assertThat(encoder.matches("NewPassword456", hash)).isTrue();
        assertThat(((Number) accountRow(buyer).get("auth_version")).intValue()).isEqualTo(1);

        // Вход, сделанный до смены пароля, больше не действует
        mvc.perform(get("/profile").with(oldSession)).andExpect(status().isUnauthorized());
    }
}
