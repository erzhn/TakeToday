package com.example.taketoday.pickup;

import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.util.UUID;

/**
 * Экран 9 «Выдача заказа и QR-сканер» (п. 4.7.3–4.7.5 ТЗ). Только для STAFF (SecurityConfig: /staff/**).
 */
@Controller
@RequestMapping("/staff/pickup")
public class PickupController {

    private final PickupService pickup;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public PickupController(PickupService pickup, CurrentUserService currentUser,
                            @Qualifier("reservationClock") Clock clock) {
        this.pickup = pickup;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping
    public String page(Model model, HttpServletResponse response) {
        fill(model, currentUser.require(), response);
        return "staff/pickup";
    }

    /** «Проверить код»: ручной ввод или результат сканирования QR. */
    @PostMapping("/check")
    public String check(@RequestParam(defaultValue = "") String code, Model model, HttpServletResponse response) {
        CurrentAccount staff = currentUser.require();
        PickupCheck result = pickup.check(code, staff);
        model.addAttribute("check", result);
        // Неверный код оставляем в поле, чтобы исправить одну цифру; верный — не показываем повторно
        model.addAttribute("code", result.order() == null ? code : "");
        fill(model, staff, response);
        return "staff/pickup";
    }

    @PostMapping("/{orderId}/confirm")
    public String confirm(@PathVariable UUID orderId, RedirectAttributes flash) {
        try {
            PickupOrder order = pickup.confirm(orderId, currentUser.require());
            flash.addFlashAttribute("success", "Заказ № " + order.getNumber() + " выдан");
            flash.addFlashAttribute("successText", order.title() + " × " + order.quantity()
                    + " — " + order.getBuyerShortName() + ". Код больше не действует.");
        } catch (PickupException e) {
            flash.addFlashAttribute("pickupError", e.getMessage());
        }
        return "redirect:/staff/pickup";
    }

    private void fill(Model model, CurrentAccount staff, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("stats", pickup.stats(staff.getEstablishmentId()));
        model.addAttribute("waiting", pickup.waiting(staff.getEstablishmentId(), 6));
        model.addAttribute("issued", pickup.recentlyIssued(staff.getEstablishmentId(), 5));
        model.addAttribute("now", clock.instant());
    }
}
