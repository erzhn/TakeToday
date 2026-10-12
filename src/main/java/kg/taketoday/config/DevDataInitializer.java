package kg.taketoday.config;

import kg.taketoday.user.Role;
import kg.taketoday.user.User;
import kg.taketoday.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Только для разработки (профиль dev): создаёт тестовых пользователей всех трёх ролей,
 * чтобы можно было проверять разделы до того, как готова регистрация.
 * Пароль у всех: password
 */
@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
    private static final String DEV_PASSWORD = "password";

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DevDataInitializer(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        create("buyer@taketoday.kg", "Тестовый покупатель", Role.BUYER);
        create("staff@taketoday.kg", "Тестовый сотрудник", Role.STAFF);
        create("admin@taketoday.kg", "Администратор", Role.ADMIN);
    }

    private void create(String email, String name, Role role) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        users.save(new User(email, encoder.encode(DEV_PASSWORD), name, role));
        log.info("Создан тестовый пользователь {} ({})", email, role);
    }
}
