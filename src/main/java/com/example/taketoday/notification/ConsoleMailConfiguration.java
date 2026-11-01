package com.example.taketoday.notification;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * Режим «письма в консоль» (SPEC, раздел 11). Включается, если:
 * <ul>
 *     <li>{@code MAIL_CONSOLE=true} ({@code app.mail.console}), или</li>
 *     <li>почта не настроена — {@code MAIL_USERNAME} пустой.</li>
 * </ul>
 * В профиле {@code prod} режим не включается никогда: там {@code MAIL_USERNAME} обязателен,
 * и без него приложение не запустится.
 *
 * <p>Наш бин {@link JavaMailSender} заменяет стандартный из Spring Boot (тот создаётся, только когда своего нет).
 */
@Configuration
public class ConsoleMailConfiguration {

    @Bean
    @Conditional(ConsoleMailCondition.class)
    public JavaMailSender consoleMailSender() {
        return new ConsoleMailSender();
    }

    static class ConsoleMailCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                return ConditionOutcome.noMatch("профиль prod — письма отправляются через SMTP");
            }
            if (env.getProperty("app.mail.console", Boolean.class, false)) {
                return ConditionOutcome.match("app.mail.console=true");
            }
            return StringUtils.hasText(env.getProperty("spring.mail.username"))
                    ? ConditionOutcome.noMatch("почта настроена (spring.mail.username)")
                    : ConditionOutcome.match("MAIL_USERNAME не задан — письма выводятся в консоль");
        }
    }
}
