package com.example.taketoday.common;

import java.util.Map;
import java.util.UUID;

/**
 * Вошедший пользователь со свежими данными из базы (имя могли поменять в профиле).
 * Используется в шапке страниц (${headerInfo}) и в контроллерах кабинета.
 * Обычный класс с геттерами, чтобы Thymeleaf точно читал поля: ${headerInfo.initials}.
 */
public final class CurrentAccount {

    private static final Map<String, String> ROLE_TITLES = Map.of(
            "CUSTOMER", "Покупатель",
            "STAFF", "Сотрудник заведения",
            "ADMIN", "Администратор");

    private final UUID id;
    private final String email;
    private final String name;
    private final String phone;
    private final String role;
    private final UUID establishmentId;
    private final String establishmentName;

    public CurrentAccount(UUID id, String email, String name, String phone, String role,
                          UUID establishmentId, String establishmentName) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.establishmentId = establishmentId;
        this.establishmentName = establishmentName;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public UUID getEstablishmentId() { return establishmentId; }
    public String getEstablishmentName() { return establishmentName; }

    public boolean isCustomer() { return "CUSTOMER".equals(role); }
    public boolean isStaff() { return "STAFF".equals(role); }
    public boolean isAdmin() { return "ADMIN".equals(role); }

    /** «Покупатель» / «Сотрудник заведения» / «Администратор» */
    public String getRoleTitle() {
        return ROLE_TITLES.getOrDefault(role, role);
    }

    /** «Айбек Д.» */
    public String getShortName() {
        String[] parts = safeName().split("\\s+");
        String first = parts[0];
        String last = parts.length > 1 ? parts[parts.length - 1] : "";
        return last.isEmpty() ? first : first + " " + last.charAt(0) + ".";
    }

    /** «АД» — для кружка-аватара */
    public String getInitials() {
        String[] parts = safeName().split("\\s+");
        String first = parts[0];
        String last = parts.length > 1 ? parts[parts.length - 1] : "";
        return (first.substring(0, 1) + (last.isEmpty() ? "" : last.substring(0, 1))).toUpperCase();
    }

    private String safeName() {
        return name == null || name.isBlank() ? email : name.trim();
    }
}
