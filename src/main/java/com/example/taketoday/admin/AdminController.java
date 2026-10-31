package com.example.taketoday.admin;

import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.refund.RefundStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Админ-панель (экран 11, п. 4.10 ТЗ). Доступ только у ADMIN — /admin/** закрыт в SecurityConfig.
 * Все действия — POST с CSRF-токеном; после действия — редирект обратно с сообщением.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final CurrentUserService currentUser;

    public AdminController(AdminService admin, CurrentUserService currentUser) {
        this.admin = admin;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("stats", admin.overview());
        model.addAttribute("manual", admin.needManualRefund());
        model.addAttribute("events", admin.events(null, null, 12));
        return "admin/overview";
    }

    // ---------- пользователи ----------

    @GetMapping("/users")
    public String users(@RequestParam(required = false) String q, @RequestParam(required = false) String role, Model model) {
        model.addAttribute("users", admin.users(q, role));
        model.addAttribute("q", q);
        model.addAttribute("role", role);
        return "admin/users";
    }

    @PostMapping("/users/{id}/block")
    public String block(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason,
                        @RequestParam(required = false) String back, RedirectAttributes flash) {
        return act(flash, back, "/admin/users", () -> {
            admin.block(id, adminId(), reason);
            return "Учётная запись заблокирована. Все её сессии завершены.";
        });
    }

    @PostMapping("/users/{id}/unblock")
    public String unblock(@PathVariable UUID id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        return act(flash, back, "/admin/users", () -> {
            admin.unblock(id, adminId());
            return "Учётная запись разблокирована.";
        });
    }

    // ---------- заведения и предложения ----------

    @GetMapping("/venues")
    public String venues(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("venues", admin.venues(q));
        model.addAttribute("q", q);
        return "admin/venues";
    }

    @GetMapping("/offers")
    public String offers(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                         Model model) {
        model.addAttribute("offers", admin.offers(q, status));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "admin/offers";
    }

    @PostMapping("/offers/{id}/hide")
    public String hide(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason,
                       @RequestParam(required = false) String back, RedirectAttributes flash) {
        return act(flash, back, "/admin/offers", () -> {
            admin.hideOffer(id, adminId(), reason);
            return "Предложение скрыто из каталога.";
        });
    }

    @PostMapping("/offers/{id}/unhide")
    public String unhide(@PathVariable UUID id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        return act(flash, back, "/admin/offers", () -> {
            admin.unhideOffer(id, adminId());
            return "Скрытие снято. Выставить предложение снова может само заведение.";
        });
    }

    // ---------- платежи, заказы, возвраты ----------

    @GetMapping("/payments")
    public String payments(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                           Model model) {
        model.addAttribute("payments", admin.payments(q, status));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "admin/payments";
    }

    @GetMapping("/refunds")
    public String refunds(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                          Model model) {
        model.addAttribute("refunds", admin.refunds(q, status));
        model.addAttribute("manual", admin.needManualRefund());
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "admin/refunds";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable UUID id, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("order", admin.order(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("payments", admin.paymentsOf(id));
        model.addAttribute("refunds", admin.refundsOf(id));
        model.addAttribute("events", admin.events(null, id, 100));
        model.addAttribute("canRefund", admin.canRefund(id));
        model.addAttribute("hasPending", admin.hasPendingRefund(id));
        return "admin/order";
    }

    /** Ручной возврат (п. 4.10.3). Оплаченный заказ при этом отменяется. */
    @PostMapping("/orders/{id}/refund")
    public String refund(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason, RedirectAttributes flash) {
        return act(flash, null, "/admin/orders/" + id, () -> refundText(admin.manualRefund(id, adminId(), reason)));
    }

    @PostMapping("/orders/{id}/refund/retry")
    public String retry(@PathVariable UUID id, RedirectAttributes flash) {
        return act(flash, null, "/admin/orders/" + id, () -> refundText(admin.retryPending(id)));
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("events", admin.events(q, null, AdminService.LIMIT));
        model.addAttribute("q", q);
        return "admin/audit";
    }

    // ---------- внутреннее ----------

    private UUID adminId() {
        return currentUser.require().getId();
    }

    /** Выполнить действие и вернуться туда, откуда пришли (только на страницы админ-панели). */
    private String act(RedirectAttributes flash, String back, String fallback, Supplier<String> action) {
        try {
            flash.addFlashAttribute("success", action.get());
        } catch (AdminException | com.example.taketoday.refund.CancelException e) {
            flash.addFlashAttribute("adminError", e.getMessage());
        }
        String target = back != null && back.startsWith("/admin") && !back.contains("//") ? back : fallback;
        return "redirect:" + target;
    }

    private static String refundText(RefundStatus status) {
        if (status == null) return "Заказ отменён.";
        return switch (status) {
            case COMPLETED -> "Деньги возвращены покупателю.";
            case PENDING -> "Возврат оформлен. Платёжная служба не ответила — повторим автоматически.";
            case FAILED -> "Возврат не выполнен: платёжная служба недоступна. Попробуйте позже.";
        };
    }
}
