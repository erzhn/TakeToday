package com.example.taketoday.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;

import static org.assertj.core.api.Assertions.assertThat;

/** Режим «письма в консоль» (SPEC, раздел 11). */
@ExtendWith(OutputCaptureExtension.class)
class ConsoleMailSenderTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsoleMailConfiguration.class);

    @Test
    void enabledWhenMailIsNotConfigured() {
        runner.withPropertyValues("spring.mail.username=")
                .run(context -> assertThat(context).hasSingleBean(ConsoleMailSender.class));
        runner.run(context -> assertThat(context).hasSingleBean(ConsoleMailSender.class));
    }

    @Test
    void disabledWhenMailIsConfigured() {
        runner.withPropertyValues("spring.mail.username=team@example.com")
                .run(context -> assertThat(context).doesNotHaveBean(ConsoleMailSender.class));
    }

    @Test
    void canBeForcedWithProperty() {
        runner.withPropertyValues("spring.mail.username=team@example.com", "app.mail.console=true")
                .run(context -> assertThat(context).hasSingleBean(ConsoleMailSender.class));
    }

    @Test
    void neverEnabledInProd() {
        runner.withPropertyValues("spring.profiles.active=prod", "spring.mail.username=", "app.mail.console=true")
                .run(context -> assertThat(context).doesNotHaveBean(ConsoleMailSender.class));
    }

    @Test
    void printsLetterToConsole(CapturedOutput output) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("buyer@example.com");
        message.setSubject("TakeToday — код подтверждения");
        message.setText("Ваш код: 482913");

        new ConsoleMailSender().send(message);

        assertThat(output.getOut())
                .contains("Кому: buyer@example.com")
                .contains("Тема: TakeToday — код подтверждения")
                .contains("Ваш код: 482913");
    }
}
