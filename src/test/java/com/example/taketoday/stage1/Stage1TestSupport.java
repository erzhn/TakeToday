package com.example.taketoday.stage1;

import com.example.taketoday.auth.AuthModels;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Общая подготовка для тестов этапа 1 (Эржан): своя база H2, пользователи создаются прямо в таблицах,
 * а «вход» делается так же, как после проверки кода в AuthController, — в сессию кладётся AuthModels.User.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stage1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "MAIL_USERNAME=test@example.com",
        "MAIL_PASSWORD=test",
        "app.admin-email=",
        "app.admin-password=",
        "app.upload-dir=target/test-uploads"
})
abstract class Stage1TestSupport {

    static final String PASSWORD = "OldPassword123";

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;

    MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        db.update("DELETE FROM establishment_work_days");
        db.update("DELETE FROM establishments");
        db.update("DELETE FROM auth_challenges");
        db.update("DELETE FROM auth_throttle");
        db.update("DELETE FROM accounts");
    }

    /** Создаёт подтверждённую учётную запись и возвращает её id. */
    UUID account(String email, String name, String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO accounts (id, email, password_hash, name, role, verified) VALUES (?, ?, ?, ?, ?, TRUE)",
                id, email, encoder.encode(PASSWORD), name, role);
        return id;
    }

    /** Сотрудник вместе с заведением — как после регистрации STAFF у Жаныбека. */
    UUID staffWithVenue(String email, String venueName) {
        UUID owner = account(email, "Сотрудник", "STAFF");
        db.update("INSERT INTO establishments (id, owner_id, name, address, contact, working_hours) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), owner, venueName, "г. Бишкек, ул. Токтогула, 1", "+996 555 000 111", "9–21");
        return owner;
    }

    Map<String, Object> venueOf(UUID owner) {
        return db.queryForMap("SELECT * FROM establishments WHERE owner_id = ?", owner);
    }

    Map<String, Object> accountRow(UUID id) {
        return db.queryForMap("SELECT * FROM accounts WHERE id = ?", id);
    }

    /** Запрос от имени пользователя (версия входа берётся из базы, как у настоящей сессии). */
    RequestPostProcessor as(UUID id) {
        Map<String, Object> row = accountRow(id);
        String role = (String) row.get("role");
        var user = new AuthModels.User(id, (String) row.get("email"), (String) row.get("name"), role,
                ((Number) row.get("auth_version")).intValue());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
