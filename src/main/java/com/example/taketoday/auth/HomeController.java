package com.example.taketoday.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.Map;
@RestController
public class HomeController {
 private final AuthService service;
 public HomeController(AuthService service){this.service=service;}
 @GetMapping("/api/public/home") Map<String,String> home(){return Map.of("title","Хорошая еда заслуживает второго шанса","description","Покупайте оставшуюся к концу дня качественную еду со скидкой и забирайте в заведении.");}
 @GetMapping("/api/customer/home") Map<String,String> customer(){return Map.of("message","Вы вошли как покупатель. Бронирование и оплата будут доступны после подключения предложений.");}
 @GetMapping("/api/staff/home") Object staff(Authentication auth){return service.establishment(((AuthModels.User)auth.getPrincipal()).id());}
 @GetMapping("/api/admin/home") Map<String,String> admin(){return Map.of("message","Вы вошли как администратор. Раздел управления будет добавлен на следующем этапе.");}
}
