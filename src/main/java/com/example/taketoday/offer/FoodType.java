package com.example.taketoday.offer;

public enum FoodType {
    BAKERY("Выпечка"), HOT("Горячее"), SALAD("Салаты"),
    DESSERT("Десерты"), SUSHI("Суши"), DRINKS("Напитки");

    private final String title;
    FoodType(String title) { this.title = title; }
    public String getTitle() { return title; }
}
