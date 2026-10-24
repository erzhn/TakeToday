package com.example.taketoday.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Шестизначный код получения (п. 4.7.1 ТЗ). Случайный (SecureRandom), не по порядку — угадать соседний код нельзя.
 * Не совпадает с кодом другого оплаченного и ещё не выданного заказа.
 */
@Component
public class PickupCodeGenerator {

    private static final int ATTEMPTS = 50;

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate db;

    public PickupCodeGenerator(JdbcTemplate db) {
        this.db = db;
    }

    public String next() {
        for (int i = 0; i < ATTEMPTS; i++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            Integer taken = db.queryForObject(
                    "SELECT COUNT(*) FROM reservation_orders WHERE pickup_code = ? AND status = 'PAID'",
                    Integer.class, code);
            if (taken == 0) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось подобрать свободный код получения");
    }
}
