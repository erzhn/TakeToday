package com.example.taketoday.admin;

import com.example.taketoday.common.CurrentUserService;
import com.example.taketoday.payment.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller
public class AdminController {
    private final JdbcTemplate db;
    private final CurrentUserService currentUser;
    private final AuditLog audit;

    public AdminController(JdbcTemplate db, CurrentUserService currentUser, AuditLog audit) {
        this.db = db;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/admin")
    public String overview(Model model) {
        putHeader(model);
        model.addAttribute("usersTotal", count("SELECT COUNT(*) FROM accounts"));
        model.addAttribute("buyersTotal", count("SELECT COUNT(*) FROM accounts WHERE role = 'CUSTOMER'"));
        model.addAttribute("staffTotal", count("SELECT COUNT(*) FROM accounts WHERE role = 'STAFF'"));
        model.addAttribute("venuesTotal", count("SELECT COUNT(*) FROM establishments"));
        model.addAttribute("activeOffersTotal", count("SELECT COUNT(*) FROM offers WHERE status = 'ACTIVE'"));
        model.addAttribute("pendingRefundsTotal", count("SELECT COUNT(*) FROM reservation_orders WHERE refund_status = 'PENDING'"));
        model.addAttribute("blockedTotal", count("SELECT COUNT(*) FROM accounts WHERE blocked = TRUE"));
        return "admin/overview";
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        putHeader(model);
        List<AdminUser> users = db.query("""
                        SELECT id, email, name, role, verified, blocked, created_at
                        FROM accounts ORDER BY created_at DESC, email
                        """,
                (rs, n) -> new AdminUser(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("name"), rs.getString("role"), rs.getBoolean("verified"),
                        rs.getBoolean("blocked"), rs.getTimestamp("created_at").toInstant()));
        model.addAttribute("users", users);
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/block")
    public String blockUser(@PathVariable UUID id, RedirectAttributes flash) {
        UUID adminId = currentUser.require().getId();
        int changed = db.update("""
                UPDATE accounts SET blocked = TRUE, auth_version = auth_version + 1
                WHERE id = ? AND id <> ? AND role <> 'ADMIN'
                """, id, adminId);
        if (changed == 1) {
            audit.log(AuditLog.ADMIN_ACCOUNT_BLOCKED, null, null, adminId, "Учётная запись заблокирована: " + id);
            flash.addFlashAttribute("success", "Учётная запись заблокирована.");
        } else flash.addFlashAttribute("error", "Нельзя заблокировать эту учётную запись.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/unblock")
    public String unblockUser(@PathVariable UUID id, RedirectAttributes flash) {
        UUID adminId = currentUser.require().getId();
        int changed = db.update("""
                UPDATE accounts SET blocked = FALSE, auth_version = auth_version + 1
                WHERE id = ? AND id <> ? AND role <> 'ADMIN'
                """, id, adminId);
        if (changed == 1) {
            audit.log(AuditLog.ADMIN_ACCOUNT_UNBLOCKED, null, null, adminId, "Учётная запись разблокирована: " + id);
            flash.addFlashAttribute("success", "Учётная запись разблокирована.");
        } else flash.addFlashAttribute("error", "Нельзя изменить эту учётную запись.");
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/offers")
    public String offers(Model model) {
        putHeader(model);
        List<AdminOffer> offers = db.query("""
                        SELECT o.id, o.title, o.status, o.total_portions, o.reserved_portions, o.sold_portions,
                               o.pickup_start, o.pickup_end, e.name AS establishment_name, a.email AS owner_email
                        FROM offers o JOIN establishments e ON e.id = o.establishment_id
                        JOIN accounts a ON a.id = e.owner_id
                        ORDER BY o.created_at DESC, o.title
                        """,
                (rs, n) -> new AdminOffer(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("establishment_name"), rs.getString("owner_email"), rs.getString("status"),
                        rs.getInt("total_portions"), rs.getInt("reserved_portions"), rs.getInt("sold_portions"),
                        rs.getTimestamp("pickup_start").toInstant(), rs.getTimestamp("pickup_end").toInstant()));
        model.addAttribute("offers", offers);
        return "admin/offers";
    }

    @PostMapping("/admin/offers/{id}/hide")
    public String hideOffer(@PathVariable UUID id, RedirectAttributes flash) {
        UUID adminId = currentUser.require().getId();
        int changed = db.update("UPDATE offers SET status='HIDDEN', updated_at=? WHERE id=? AND status='ACTIVE'",
                Timestamp.from(Instant.now()), id);
        if (changed == 1) {
            audit.log(AuditLog.ADMIN_OFFER_HIDDEN, null, null, adminId, "Предложение скрыто: " + id);
            flash.addFlashAttribute("success", "Предложение скрыто из каталога.");
        } else flash.addFlashAttribute("error", "Предложение уже скрыто или не найдено.");
        return "redirect:/admin/offers";
    }

    @PostMapping("/admin/offers/{id}/restore")
    public String restoreOffer(@PathVariable UUID id, RedirectAttributes flash) {
        UUID adminId = currentUser.require().getId();
        int changed = db.update("UPDATE offers SET status='ACTIVE', updated_at=? WHERE id=? AND status='HIDDEN'",
                Timestamp.from(Instant.now()), id);
        if (changed == 1) {
            audit.log(AuditLog.ADMIN_OFFER_RESTORED, null, null, adminId, "Предложение возвращено в каталог: " + id);
            flash.addFlashAttribute("success", "Предложение возвращено в каталог.");
        } else flash.addFlashAttribute("error", "Предложение уже активно или не найдено.");
        return "redirect:/admin/offers";
    }

    @GetMapping("/admin/payments")
    public String payments(Model model) {
        putHeader(model);
        model.addAttribute("payments", db.query("""
                        SELECT p.id, p.order_id, p.amount, p.status, p.provider_payment_id, p.card_last4,
                               p.error_message, p.created_at, p.completed_at, r.title,
                               a.name AS customer_name, a.email AS customer_email, e.name AS establishment_name
                        FROM payments p JOIN reservation_orders r ON r.id = p.order_id
                        JOIN accounts a ON a.id = r.customer_id JOIN offers o ON o.id = r.offer_id
                        JOIN establishments e ON e.id = o.establishment_id
                        ORDER BY p.created_at DESC, p.id DESC
                        """, (rs, n) -> new AdminPayment(rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class), rs.getString("title"), rs.getString("establishment_name"),
                        rs.getString("customer_name"), rs.getString("customer_email"), rs.getInt("amount"),
                        rs.getString("status"), rs.getString("provider_payment_id"), rs.getString("card_last4"),
                        rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant())));
        return "admin/payments";
    }

    @GetMapping("/admin/refunds")
    public String refunds(Model model) {
        putHeader(model);
        model.addAttribute("refunds", db.query("""
                        SELECT r.id, r.title, r.amount, r.status, r.cancel_reason, r.refund_status,
                               r.created_at, a.name AS customer_name, a.email AS customer_email, e.name AS establishment_name
                        FROM reservation_orders r JOIN accounts a ON a.id = r.customer_id
                        JOIN offers o ON o.id = r.offer_id JOIN establishments e ON e.id = o.establishment_id
                        WHERE r.refund_status <> 'NONE'
                        ORDER BY CASE WHEN r.refund_status = 'PENDING' THEN 0 ELSE 1 END, r.created_at DESC
                        """, (rs, n) -> new AdminRefund(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("establishment_name"), rs.getString("customer_name"), rs.getString("customer_email"),
                        rs.getInt("amount"), rs.getString("status"), rs.getString("cancel_reason"),
                        rs.getString("refund_status"), rs.getTimestamp("created_at").toInstant())));
        return "admin/refunds";
    }

    @PostMapping("/admin/refunds/{id}/complete")
    public String completeRefund(@PathVariable UUID id, RedirectAttributes flash) {
        UUID adminId = currentUser.require().getId();
        int changed = db.update("UPDATE reservation_orders SET refund_status='COMPLETED' WHERE id=? AND refund_status='PENDING'", id);
        if (changed == 1) {
            audit.log(AuditLog.ADMIN_REFUND_COMPLETED, id, null, adminId, "Возврат отмечен выполненным администратором");
            flash.addFlashAttribute("success", "Возврат отмечен выполненным.");
        } else flash.addFlashAttribute("error", "Возврат уже обработан или не найден.");
        return "redirect:/admin/refunds";
    }

    private void putHeader(Model model) { model.addAttribute("headerInfo", currentUser.require()); }
    private long count(String sql) { Long value = db.queryForObject(sql, Long.class); return value == null ? 0 : value; }

    public record AdminUser(UUID id, String email, String name, String role, boolean verified, boolean blocked, Instant createdAt) {}
    public record AdminOffer(UUID id, String title, String establishmentName, String ownerEmail, String status,
                             int totalPortions, int reservedPortions, int soldPortions, Instant pickupStart, Instant pickupEnd) {
        public int available() { return Math.max(0, totalPortions - reservedPortions - soldPortions); }
    }
    public record AdminPayment(UUID id, UUID orderId, String title, String establishmentName, String customerName,
                               String customerEmail, int amount, String status, String providerPaymentId,
                               String cardLast4, String errorMessage, Instant createdAt, Instant completedAt) {}
    public record AdminRefund(UUID id, String title, String establishmentName, String customerName, String customerEmail,
                              int amount, String orderStatus, String cancelReason, String refundStatus, Instant createdAt) {}
}
