package com.example.taketoday.common;

import java.nio.charset.StandardCharsets;

/**
 * Общие правила проверки полей и тексты ошибок (docs/SPEC.md, раздел 14).
 * Правило пароля совпадает с регистрацией (AuthModels): от 10 символов, не больше 72 байт UTF-8.
 */
public final class ValidationRules {

    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX_BYTES = 72;
    public static final String PASSWORD_MESSAGE = "Пароль: от 10 символов, не более 72 байт UTF-8";

    /** +996 и 9 цифр, пробелы, дефисы и скобки допускаются. Пустое значение тоже допустимо. */
    public static final String PHONE_REGEX = "^$|^\\+?\\s*996[\\s\\-()]*(\\d[\\s\\-()]*){9}$";
    public static final String PHONE_MESSAGE = "Телефон в формате +996 700 123 456";

    public static final String NAME_MESSAGE = "Введите имя";
    public static final String PASSWORDS_MISMATCH = "Пароли не совпадают";

    private ValidationRules() {
    }

    /** Пароль не длиннее 72 байт — столько BCrypt реально учитывает. */
    public static boolean fitsPasswordBytes(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    /**
     * Приводит телефон к виду «+996 700 123 456». Пустую строку превращает в null.
     * Вызывать только после проверки PHONE_REGEX.
     */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("996")) {
            digits = digits.substring(3);
        }
        if (digits.length() != 9) {
            return raw.trim();
        }
        return "+996 " + digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
    }

    /** Убирает пробелы по краям; пустую строку превращает в null. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
