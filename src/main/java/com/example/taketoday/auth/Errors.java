package com.example.taketoday.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.*;
import org.springframework.mail.MailException;
import java.util.Map;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Проверьте email, имя, роль и пароль (10–72 символа)."));}
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> json(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Некорректный формат запроса."));}
 @ExceptionHandler(MailException.class) ResponseEntity<?> mail(Exception e){return ResponseEntity.status(503).body(Map.of("message","Не удалось отправить письмо. Попробуйте позже."));}
}
