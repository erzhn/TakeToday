package com.example.taketoday.catalog;

import com.example.taketoday.offer.FoodType;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalTime;

public class CatalogFilter {
    @Size(max = 150, message = "Поиск: не больше 150 символов")
    private String q = "";
    private FoodType type;
    @Min(value = 0, message = "Цена не может быть отрицательной")
    @Max(value = 100000, message = "Цена не больше 100 000 сом")
    private Integer minPrice;
    @Min(value = 0, message = "Цена не может быть отрицательной")
    @Max(value = 100000, message = "Цена не больше 100 000 сом")
    private Integer maxPrice;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pickupAfter;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pickupBefore;
    @Min(value = 0, message = "Номер страницы не может быть отрицательным")
    @Max(value = 100000, message = "Слишком большой номер страницы")
    private int page;
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q == null ? "" : q.trim(); }
    public FoodType getType() { return type; }
    public void setType(FoodType type) { this.type = type; }
    public Integer getMinPrice() { return minPrice; }
    public void setMinPrice(Integer value) { minPrice = value; }
    public Integer getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Integer value) { maxPrice = value; }
    public LocalTime getPickupAfter() { return pickupAfter; }
    public void setPickupAfter(LocalTime value) { pickupAfter = value; }
    public LocalTime getPickupBefore() { return pickupBefore; }
    public void setPickupBefore(LocalTime value) { pickupBefore = value; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
}
