package kg.taketoday.user;

/**
 * Роли пользователей (раздел 3 ТЗ).
 */
public enum Role {
    BUYER("Покупатель"),
    STAFF("Сотрудник заведения"),
    ADMIN("Администратор");

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Имя полномочия для Spring Security: ROLE_BUYER, ROLE_STAFF, ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
