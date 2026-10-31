package com.example.taketoday.admin;

/** Действие администратора невозможно — текст для него. */
public class AdminException extends RuntimeException {
    public AdminException(String message) {
        super(message);
    }
}
