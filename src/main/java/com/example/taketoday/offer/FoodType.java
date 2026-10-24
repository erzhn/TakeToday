package com.example.taketoday.offer;

/** Вид еды: фильтр каталога (Жаныбек) и форма предложения (Эржан). Иконка — для кабинета заведения. */
public enum FoodType {
    BAKERY("Выпечка", "bakery_dining"), HOT("Горячее", "soup_kitchen"), SALAD("Салаты", "nutrition"),
    DESSERT("Десерты", "cake"), SUSHI("Суши", "set_meal"), DRINKS("Напитки", "local_cafe");

    private final String title;
    private final String icon;
    FoodType(String title, String icon) { this.title = title; this.icon = icon; }
    public String getTitle() { return title; }
    /** Название иконки Material Symbols. */
    public String getIcon() { return icon; }
}
