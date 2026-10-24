package com.example.taketoday.offer.dto;

import com.example.taketoday.offer.FoodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Форма «Новое предложение» / «Изменить предложение» (экран 8, п. 4.4.1–4.4.2 ТЗ, SPEC раздел 14).
 * Проверки, которые зависят от нескольких полей или от базы, — в OfferService.validate.
 */
public class OfferForm {

    @NotBlank(message = "Введите название набора")
    @Size(min = 3, max = 150, message = "Название: от 3 до 150 символов")
    private String title;

    @NotNull(message = "Выберите вид еды")
    private FoodType foodType;

    @NotBlank(message = "Опишите состав набора")
    @Size(min = 5, max = 1000, message = "Состав: от 5 до 1000 символов")
    private String composition;

    /** Новое фото. Обязательно при создании, при изменении — только если хотите заменить. */
    private MultipartFile photo;

    @NotNull(message = "Укажите цену")
    @Min(value = 1, message = "Цена: от 1 до 100 000 сом")
    @Max(value = 100_000, message = "Цена: от 1 до 100 000 сом")
    private Integer regularPrice;

    @NotNull(message = "Укажите цену со скидкой")
    @Min(value = 1, message = "Цена со скидкой должна быть больше 0")
    private Integer discountPrice;

    /** Отрицательное и нулевое значения проверяет сервис — у них свои тексты ошибок. */
    @NotNull(message = "Укажите количество порций")
    @Max(value = 100, message = "Не больше 100 порций")
    private Integer totalPortions;

    @NotNull(message = "Укажите дату выдачи")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate pickupDate;

    @NotNull(message = "Укажите время начала выдачи")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pickupStart;

    @NotNull(message = "Укажите время окончания выдачи")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pickupEnd;

    /** «Повторить»: из какого предложения взять фото, если новое не загружено. */
    private UUID copyFrom;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public FoodType getFoodType() { return foodType; }
    public void setFoodType(FoodType foodType) { this.foodType = foodType; }

    public String getComposition() { return composition; }
    public void setComposition(String composition) { this.composition = composition; }

    public MultipartFile getPhoto() { return photo; }
    public void setPhoto(MultipartFile photo) { this.photo = photo; }

    public Integer getRegularPrice() { return regularPrice; }
    public void setRegularPrice(Integer regularPrice) { this.regularPrice = regularPrice; }

    public Integer getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(Integer discountPrice) { this.discountPrice = discountPrice; }

    public Integer getTotalPortions() { return totalPortions; }
    public void setTotalPortions(Integer totalPortions) { this.totalPortions = totalPortions; }

    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate pickupDate) { this.pickupDate = pickupDate; }

    public LocalTime getPickupStart() { return pickupStart; }
    public void setPickupStart(LocalTime pickupStart) { this.pickupStart = pickupStart; }

    public LocalTime getPickupEnd() { return pickupEnd; }
    public void setPickupEnd(LocalTime pickupEnd) { this.pickupEnd = pickupEnd; }

    public UUID getCopyFrom() { return copyFrom; }
    public void setCopyFrom(UUID copyFrom) { this.copyFrom = copyFrom; }

    public boolean hasNewPhoto() {
        return photo != null && !photo.isEmpty();
    }
}
