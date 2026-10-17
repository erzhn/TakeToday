package com.example.taketoday.venue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Попытка работать с заведением, к которому у пользователя нет доступа. Показывается как страница 403. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class VenueAccessException extends RuntimeException {

    public VenueAccessException(String message) {
        super(message);
    }
}
