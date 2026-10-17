package com.example.taketoday.profile.dto;

import com.example.taketoday.common.ValidationRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Форма «Смена пароля» (п. 4.2.1 ТЗ). Правило пароля — как при регистрации. */
public class PasswordForm {

    @NotBlank(message = "Введите текущий пароль")
    private String currentPassword;

    @NotBlank(message = "Введите новый пароль")
    @Size(min = ValidationRules.PASSWORD_MIN, max = ValidationRules.PASSWORD_MAX_BYTES,
            message = ValidationRules.PASSWORD_MESSAGE)
    private String newPassword;

    @NotBlank(message = "Повторите новый пароль")
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
