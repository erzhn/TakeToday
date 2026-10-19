package com.example.taketoday.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.*;
import org.springframework.mail.MailException;
import java.util.Map;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
 /** Тексты по полям, чтобы пользователь видел, что именно исправить (п. 5.5.3 ТЗ). */
 private static final Map<String,String> FIELD_MESSAGES=Map.of(
 "email","Введите почту в формате name@mail.kg.",
 "password","Пароль: от 10 до 72 символов.",
 "name","Введите имя (не больше 100 символов).",
 "role","Выберите, кто вы: покупатель или заведение.",
 "establishmentName","Название заведения: не больше 150 символов.",
 "address","Адрес заведения: не больше 300 символов.",
 "contact","Контакт заведения: не больше 150 символов.",
 "workingHours","Часы работы: не больше 150 символов.",
 "code","Код из письма — 6 цифр.",
 "challengeId","Код недействителен. Запросите новый.");
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){
 var messages=new java.util.LinkedHashSet<String>();
 for(var error:e.getBindingResult().getFieldErrors())messages.add(FIELD_MESSAGES.getOrDefault(error.getField(),"Проверьте поле «"+error.getField()+"»."));
 String message=messages.isEmpty()?"Проверьте заполнение формы.":String.join(" ",messages);
 return ResponseEntity.badRequest().body(Map.of("message",message));}
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> json(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Некорректный формат запроса."));}
 @ExceptionHandler(MailException.class) ResponseEntity<?> mail(Exception e){return ResponseEntity.status(503).body(Map.of("message","Не удалось отправить письмо. Попробуйте позже."));}
}
