package com.example.taketoday.stage3;

import com.example.taketoday.payment.mock.MockCard;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверка полей карты на учебной странице оплаты. */
class MockCardTests {

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);

    @Test
    void testCardsPass() {
        assertTrue(MockCard.validate("4242 4242 4242 4242", "12/28", "123", OCT_2026).isEmpty());
        assertTrue(MockCard.validate("4000-0000-0000-0002", "10/26", "000", OCT_2026).isEmpty(), "текущий месяц ещё действует");
    }

    @Test
    void mistakesAreReported() {
        assertEquals(1, MockCard.validate("4242 4242 4242 4241", "12/28", "123", OCT_2026).size(), "контрольная цифра");
        assertEquals(1, MockCard.validate("4242", "12/28", "123", OCT_2026).size(), "короткий номер");
        assertEquals(1, MockCard.validate("4242424242424242", "09/26", "123", OCT_2026).size(), "срок истёк");
        assertEquals(1, MockCard.validate("4242424242424242", "13/28", "123", OCT_2026).size(), "нет 13-го месяца");
        assertEquals(1, MockCard.validate("4242424242424242", "12/28", "12a", OCT_2026).size(), "CVV");
        assertEquals(3, MockCard.validate(null, null, null, OCT_2026).size());
    }
}
