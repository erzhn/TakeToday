package com.example.taketoday.profile;

import com.example.taketoday.common.CurrentAccount;
import com.example.taketoday.common.ValidationRules;
import com.example.taketoday.profile.dto.PasswordForm;
import com.example.taketoday.profile.dto.ProfileForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.UUID;

/**
 * Личный кабинет: имя, телефон и пароль (п. 4.2.1 ТЗ).
 * Пользователь меняет только СВОИ данные — id всегда берётся из сессии, а не из формы.
 * Работает с таблицей accounts (V2__authentication.sql) через JdbcTemplate, как и AuthService.
 */
@Service
public class ProfileService {

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public ProfileService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    public ProfileForm toForm(CurrentAccount account) {
        ProfileForm form = new ProfileForm();
        form.setName(account.getName());
        form.setPhone(account.getPhone());
        return form;
    }

    @Transactional
    public void updateProfile(UUID accountId, ProfileForm form) {
        db.update("UPDATE accounts SET name = ?, phone = ? WHERE id = ?",
                form.getName().trim().replaceAll("\\s+", " "),
                ValidationRules.normalizePhone(form.getPhone()),
                accountId);
    }

    /** Проверки, которые зависят от базы или от двух полей сразу. Ошибки кладутся под нужное поле. */
    @Transactional(readOnly = true)
    public void validatePasswordChange(UUID accountId, PasswordForm form, Errors errors) {
        String current = form.getCurrentPassword();
        if (current != null && !current.isEmpty()
                && !encoder.matches(current, passwordHash(accountId))) {
            errors.rejectValue("currentPassword", "wrong", "Текущий пароль указан неверно");
        }
        String next = form.getNewPassword();
        if (next != null && !ValidationRules.fitsPasswordBytes(next)) {
            errors.rejectValue("newPassword", "bytes", ValidationRules.PASSWORD_MESSAGE);
        }
        if (next != null && form.getConfirmPassword() != null && !form.getConfirmPassword().isEmpty()
                && !next.equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", ValidationRules.PASSWORDS_MISMATCH);
        }
        if (next != null && next.equals(current)) {
            errors.rejectValue("newPassword", "same", "Новый пароль должен отличаться от текущего");
        }
    }

    /**
     * Меняет пароль и увеличивает auth_version. SessionGuard (Жаныбек) после этого
     * отключит все старые сессии на других устройствах. Возвращает новый auth_version,
     * чтобы контроллер сохранил его в текущей сессии и пользователя не выкинуло.
     */
    @Transactional
    public int changePassword(UUID accountId, PasswordForm form) {
        db.update("UPDATE accounts SET password_hash = ?, auth_version = auth_version + 1 WHERE id = ?",
                encoder.encode(form.getNewPassword()), accountId);
        Integer version = db.queryForObject("SELECT auth_version FROM accounts WHERE id = ?", Integer.class, accountId);
        return version == null ? 0 : version;
    }

    private String passwordHash(UUID accountId) {
        return db.queryForObject("SELECT password_hash FROM accounts WHERE id = ?", String.class, accountId);
    }
}
