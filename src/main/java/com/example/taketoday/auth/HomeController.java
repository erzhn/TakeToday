package com.example.taketoday.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.Map;
@RestController
public class HomeController {
 private final AuthService service;
 public HomeController(AuthService service){this.service=service;}
 @GetMapping("/api/public/home") Map<String,String> home(){return Map.of("title","Хорошая еда заслуживает второго шанса","description","Покупайте оставшуюся к концу дня качественную еду со скидкой и забирайте в заведении.");}
 @GetMapping("/api/customer/home") Map<String,String> customer(){return Map.of("message","Выберите набор в каталоге, оплатите онлайн и заберите по коду в заведении. Ваши заказы — в разделе «Мои заказы».");}
 @GetMapping("/api/staff/home") Object staff(Authentication auth){return service.establishment(((AuthModels.User)auth.getPrincipal()).id());}
 @GetMapping("/api/admin/home") Map<String,String> admin(){return Map.of("message","Вы вошли как администратор. Пользователи, предложения, платежи и возвраты — в админ-панели.");}
}
