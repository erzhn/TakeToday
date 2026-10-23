package com.example.taketoday.reservation;

import com.example.taketoday.auth.ApiError;
import com.example.taketoday.common.CurrentUserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ReservationController {
    private final ReservationService reservations;
    private final CurrentUserService currentUser;
    private final Clock clock;
    public ReservationController(ReservationService reservations, CurrentUserService currentUser,
                                 @Qualifier("reservationClock") Clock clock) {
        this.reservations = reservations; this.currentUser = currentUser; this.clock = clock;
    }
    public record CreateRequest(@NotNull UUID offerId, @NotNull UUID requestKey, @NotNull @Min(1) Integer quantity) {}
    public record State(Reservation order, Instant serverNow) {}

    @PostMapping("/api/orders/reservations")
    @ResponseBody
    public State create(@Valid @RequestBody CreateRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Reservation order = reservations.create(customer(), request.offerId(), request.requestKey(), request.quantity());
        return new State(order, clock.instant());
    }

    @GetMapping("/api/orders/reservations/{id}")
    @ResponseBody
    public State state(@PathVariable UUID id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Reservation order = reservations.get(id, customer());
        return new State(order, clock.instant());
    }

    @GetMapping("/reservations/{id}")
    public String page(@PathVariable UUID id, Model model, HttpServletResponse response) {
        UUID customer = customer();
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("order", reservations.get(id, customer));
        model.addAttribute("serverNow", clock.instant());
        model.addAttribute("headerInfo", currentUser.require());
        return "reservation/checkout";
    }

    private UUID customer() {
        var user = currentUser.principal().orElseThrow(() -> new ApiError(HttpStatus.UNAUTHORIZED, "Войдите в учетную запись."));
        if (!"CUSTOMER".equals(user.role()))
            throw new ApiError(HttpStatus.FORBIDDEN, "Бронирование доступно только покупателям.");
        return user.id();
    }
}
