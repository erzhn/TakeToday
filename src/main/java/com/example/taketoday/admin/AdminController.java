package com.example.taketoday.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Админ-панель. На этапе 1 — только обзор, чтобы проверить доступ по роли.
 * Разделы «Пользователи», «Предложения», «Платежи», «Возвраты» — этап 5.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final JdbcTemplate db;

    public AdminController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("usersTotal", count("SELECT COUNT(*) FROM accounts"));
        model.addAttribute("buyersTotal", count("SELECT COUNT(*) FROM accounts WHERE role = 'CUSTOMER'"));
        model.addAttribute("staffTotal", count("SELECT COUNT(*) FROM accounts WHERE role = 'STAFF'"));
        model.addAttribute("venuesTotal", count("SELECT COUNT(*) FROM establishments"));
        return "admin/overview";
    }

    private long count(String sql) {
        Long value = db.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
