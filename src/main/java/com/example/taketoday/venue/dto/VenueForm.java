package com.example.taketoday.venue.dto;

import com.example.taketoday.common.ValidationRules;
import com.example.taketoday.venue.District;
import com.example.taketoday.venue.VenueCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/** Форма «Сведения о заведении» (п. 4.2.2 ТЗ, SPEC раздел 14). Ограничения длины — как в таблице establishments. */
public class VenueForm {

    @NotBlank(message = "Введите название заведения")
    @Size(min = 2, max = 150, message = "Название: от 2 до 150 символов")
    private String name;

    private VenueCategory category;

    @Size(max = 500, message = "Описание: не больше 500 символов")
    private String description;

    @NotBlank(message = "Укажите адрес")
    @Size(max = 300, message = "Адрес: не больше 300 символов")
    private String address;

    private District district;

    /** Хранится в establishments.contact — это поле заполняется ещё при регистрации. */
    @NotBlank(message = "Укажите телефон для связи")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String phone;

    @Email(message = "Введите почту в формате name@mail.kg")
    @Size(max = 254, message = "Почта: не больше 254 символов")
    private String contactEmail;

    @Size(max = 100, message = "Не больше 100 символов")
    private String instagram;

    @Valid
    private List<WorkDayForm> workDays = new ArrayList<>();

    /** Новый логотип (необязательно). */
    private MultipartFile logo;

    /** Отмечено «Удалить логотип». */
    private boolean removeLogo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public VenueCategory getCategory() { return category; }
    public void setCategory(VenueCategory category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public District getDistrict() { return district; }
    public void setDistrict(District district) { this.district = district; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }

    public List<WorkDayForm> getWorkDays() { return workDays; }
    public void setWorkDays(List<WorkDayForm> workDays) { this.workDays = workDays; }

    public MultipartFile getLogo() { return logo; }
    public void setLogo(MultipartFile logo) { this.logo = logo; }

    public boolean isRemoveLogo() { return removeLogo; }
    public void setRemoveLogo(boolean removeLogo) { this.removeLogo = removeLogo; }
}
