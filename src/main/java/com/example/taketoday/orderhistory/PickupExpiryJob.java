package com.example.taketoday.orderhistory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Каждые 5 секунд переводит неполученные оплаченные заказы в «Время получения истекло» (п. 4.8.2).
 * Выключается тем же свойством, что и снятие просроченных броней: {@code app.reservations.expiry-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.reservations.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class PickupExpiryJob {

    private final PickupExpiryService expiry;

    public PickupExpiryJob(PickupExpiryService expiry) {
        this.expiry = expiry;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void expire() {
        expiry.expireDue();
    }
}
