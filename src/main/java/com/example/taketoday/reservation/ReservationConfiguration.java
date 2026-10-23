package com.example.taketoday.reservation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReservationConfiguration {
    @Bean public Clock reservationClock() { return Clock.systemUTC(); }
}
