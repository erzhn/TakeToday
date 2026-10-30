package com.example.taketoday.pickup;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Что ввёл или отсканировал сотрудник: «482913», «482 913» или содержимое QR-кода
 * «TAKETODAY:{номер заказа}:{код}» (его делает QrCodeImage на этапе 3).
 */
public record PickupCodeInput(String code, UUID orderId) {

    private static final Pattern QR = Pattern.compile(
            "^TAKETODAY:([0-9a-fA-F-]{36}):(\\d{6})$");

    public static Optional<PickupCodeInput> parse(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.trim();
        Matcher qr = QR.matcher(value);
        if (qr.matches()) {
            try {
                return Optional.of(new PickupCodeInput(qr.group(2), UUID.fromString(qr.group(1))));
            } catch (IllegalArgumentException badUuid) {
                return Optional.empty();
            }
        }
        String digits = value.replaceAll("[\\s-]", "");
        return digits.matches("\\d{6}") ? Optional.of(new PickupCodeInput(digits, null)) : Optional.empty();
    }
}
