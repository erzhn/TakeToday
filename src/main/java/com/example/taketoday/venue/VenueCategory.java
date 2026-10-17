package com.example.taketoday.venue;

/** Категория заведения — показывается в каталоге и на карточке заведения. */
public enum VenueCategory {
    BAKERY("Пекарня и выпечка"),
    COFFEE_SHOP("Кофейня"),
    CAFE("Кафе"),
    RESTAURANT("Ресторан"),
    CONFECTIONERY("Кондитерская"),
    SUSHI_BAR("Суши-бар"),
    OTHER("Другое");

    private final String title;

    VenueCategory(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
