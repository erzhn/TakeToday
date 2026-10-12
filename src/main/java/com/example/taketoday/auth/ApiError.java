package com.example.taketoday.auth;
import org.springframework.http.HttpStatus;
public class ApiError extends RuntimeException {
 final HttpStatus status;
 public ApiError(HttpStatus status, String message) { super(message); this.status=status; }
}
