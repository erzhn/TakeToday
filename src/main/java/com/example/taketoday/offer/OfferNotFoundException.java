package com.example.taketoday.offer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Предложения нет или оно чужое — сотруднику отвечаем одинаково «не найдено», чтобы не подсказывать чужие id. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OfferNotFoundException extends RuntimeException {
    public OfferNotFoundException() {
        super("Предложение не найдено");
    }
}
