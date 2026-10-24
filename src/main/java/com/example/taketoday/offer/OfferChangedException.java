package com.example.taketoday.offer;

/** Предложение изменилось, пока сотрудник редактировал форму. Текст показываем над формой. */
public class OfferChangedException extends RuntimeException {
    public OfferChangedException(String message) {
        super(message);
    }
}
