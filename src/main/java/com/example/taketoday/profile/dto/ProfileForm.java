package com.example.taketoday.profile.dto;

import com.example.taketoday.common.ValidationRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Форма «Личные данные» (п. 4.2.1 ТЗ). Почту менять нельзя, поэтому её здесь нет. */
public class ProfileForm {

    @NotBlank(message = ValidationRules.NAME_MESSAGE)
    @Size(min = 2, max = 100, message = "Имя: от 2 до 100 символов")
    private String name;

    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String phone;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
