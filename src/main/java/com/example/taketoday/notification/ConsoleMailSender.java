package com.example.taketoday.notification;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Письма не отправляются, а печатаются в консоль (SPEC, раздел 11): для разработки без почтового сервера.
 * Коды входа, чеки, напоминания и письма об отмене видны прямо в логе приложения.
 * Включается {@link ConsoleMailConfiguration}; в профиле {@code prod} не используется никогда.
 */
public class ConsoleMailSender implements JavaMailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailSender.class);
    private static final String LINE = "=".repeat(64);

    @Override
    public void send(SimpleMailMessage... messages) {
        for (SimpleMailMessage message : messages) {
            print(join(message.getTo()), message.getSubject(), message.getText());
        }
    }

    @Override
    public void send(MimeMessage... messages) {
        for (MimeMessage message : messages) {
            try {
                print(join(message.getAllRecipients()), message.getSubject(), String.valueOf(message.getContent()));
            } catch (MessagingException | IOException e) {
                throw new MailParseException("Не удалось прочитать письмо для вывода в консоль", e);
            }
        }
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        try {
            return new MimeMessage(Session.getInstance(new Properties()), contentStream);
        } catch (MessagingException e) {
            throw new MailParseException("Не удалось прочитать письмо", e);
        }
    }

    private static void print(String to, String subject, String text) {
        log.info("\n{}\nПисьмо не отправлено — почта выводится в консоль (MAIL_USERNAME не задан)\nКому: {}\nТема: {}\n\n{}\n{}",
                LINE, to, subject, text, LINE);
    }

    private static String join(String[] values) {
        return values == null ? "" : String.join(", ", values);
    }

    private static String join(Address[] values) {
        return values == null ? "" : Arrays.stream(values).map(Address::toString).collect(Collectors.joining(", "));
    }
}
