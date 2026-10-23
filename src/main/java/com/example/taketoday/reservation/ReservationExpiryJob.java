package com.example.taketoday.reservation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reservations.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJob {
    private final ReservationService reservations;
    public ReservationExpiryJob(ReservationService reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelay = 5000)
    public void expire() { reservations.expireDue(); }
}
