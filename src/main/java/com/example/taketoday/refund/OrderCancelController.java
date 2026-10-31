package com.example.taketoday.refund;

import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.payment.CustomerGuard;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Кнопки отмены: покупатель (бронь и оплаченный заказ) и заведение («не можем выдать»). */
@Controller
public class OrderCancelController {

    private final OrderCancelService cancels;
    private final CustomerGuard guard;
    private final CurrentUserService currentUser;

    public OrderCancelController(OrderCancelService cancels, CustomerGuard guard, CurrentUserService currentUser) {
        this.cancels = cancels;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    /** «Отменить бронь и уступить порцию» на странице оформления (экран 3). */
    @PostMapping("/reservations/{id}/cancel")
    public String cancelReservation(@PathVariable UUID id, RedirectAttributes flash) {
        UUID customer = guard.customerId();
        try {
            cancels.cancelByCustomer(id, customer);
            flash.addFlashAttribute("cancelMessage", "Бронь отменена, порции вернулись в продажу.");
            return "redirect:/orders/" + id;
        } catch (CancelException e) {
            // Страница брони сама покажет, что с заказом (истекла бронь, уже оплачен)
            flash.addFlashAttribute("paymentError", e.getMessage());
            return "redirect:/reservations/" + id;
        }
    }

    /** «Отменить заказ» на странице заказа (экран 4) — до начала выдачи, деньги возвращаются автоматически. */
    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable UUID id, RedirectAttributes flash) {
        UUID customer = guard.customerId();
        try {
            OrderCancelService.Result result = cancels.cancelByCustomer(id, customer);
            flash.addFlashAttribute("cancelMessage", result.wasPaid()
                    ? "Заказ отменён. " + refundText(result.refund())
                    : "Бронь отменена, порции вернулись в продажу.");
        } catch (CancelException e) {
            flash.addFlashAttribute("cancelError", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    /** Заведение не может выдать оплаченный заказ — отмена с автоматическим возвратом (п. 4.6.4). */
    @PostMapping("/staff/pickup/{id}/cancel")
    public String cancelByVenue(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason,
                                RedirectAttributes flash) {
        CurrentAccount staff = currentUser.require();
        if (reason.isBlank()) {
            flash.addFlashAttribute("pickupError", "Выберите причину отмены — её увидит покупатель.");
            return "redirect:/staff/pickup";
        }
        try {
            OrderCancelService.Result result = cancels.cancelByVenue(id, staff.getId(), staff.getEstablishmentId(), reason);
            flash.addFlashAttribute("success", "Заказ отменён");
            flash.addFlashAttribute("successText", refundText(result.refund()));
        } catch (CancelException e) {
            flash.addFlashAttribute("pickupError", e.getMessage());
        }
        return "redirect:/staff/pickup";
    }

    static String refundText(RefundStatus status) {
        if (status == null) return "";
        return switch (status) {
            case COMPLETED -> "Деньги вернулись на карту.";
            case PENDING -> "Возврат денег оформлен и выполняется.";
            case FAILED -> "Возврат денег не выполнен автоматически — им займётся администратор.";
        };
    }
}
