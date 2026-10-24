package com.example.taketoday.payment.mock;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Проверка данных карты на учебной странице оплаты. Карта никуда не сохраняется —
 * наружу выходят только последние 4 цифры.
 *
 * <ul>
 *     <li>4242 4242 4242 4242 — оплата проходит;</li>
 *     <li>4000 0000 0000 0002 — отказ «Недостаточно средств на карте»;</li>
 *     <li>любой другой правильный номер — оплата проходит.</li>
 * </ul>
 */
public final class MockCard {

    public static final String SUCCESS_CARD = "4242424242424242";
    public static final String DECLINED_CARD = "4000000000000002";

    private MockCard() {
    }

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("[\\s-]", "");
    }

    /** Ошибки заполнения. Пустой список — карту можно отправлять в банк. */
    public static List<String> validate(String number, String expiry, String cvv, YearMonth now) {
        List<String> errors = new ArrayList<>();
        String card = digits(number);
        if (!card.matches("\\d{13,19}") || !luhn(card)) {
            errors.add("Проверьте номер карты — в нём ошибка.");
        }
        String exp = expiry == null ? "" : expiry.replace(" ", "");
        if (!exp.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            errors.add("Срок действия — в формате ММ/ГГ, например 08/28.");
        } else {
            YearMonth until = YearMonth.of(2000 + Integer.parseInt(exp.substring(3)), Integer.parseInt(exp.substring(0, 2)));
            if (until.isBefore(now)) {
                errors.add("Срок действия карты истёк.");
            }
        }
        if (cvv == null || !cvv.matches("\\d{3}")) {
            errors.add("CVV — 3 цифры с обратной стороны карты.");
        }
        return errors;
    }

    static boolean luhn(String card) {
        int sum = 0;
        boolean twice = false;
        for (int i = card.length() - 1; i >= 0; i--) {
            int d = card.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
