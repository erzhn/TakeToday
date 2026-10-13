package com.example.taketoday.auth;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
@Configuration
public class AdminBootstrap {
 @Bean ApplicationRunner createAdmin(JdbcTemplate db,PasswordEncoder encoder,@Value("${app.admin-email}") String email,@Value("${app.admin-password}") String password){return args->{
 if(email.isBlank()&&password.isBlank())return;
 if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")||password.length()<10||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalStateException("Укажите корректные ADMIN_EMAIL и ADMIN_PASSWORD (10–72 байта).");
 var existing=db.queryForList("SELECT role FROM accounts WHERE email=?",email.strip().toLowerCase(Locale.ROOT));
 if(!existing.isEmpty()){if(!"ADMIN".equals(existing.get(0).get("role")))throw new IllegalStateException("ADMIN_EMAIL уже занят другой ролью");return;}
 db.update("INSERT INTO accounts(id,email,password_hash,name,role,verified) VALUES (?,?,?,?,?,TRUE)",UUID.randomUUID(),email.strip().toLowerCase(Locale.ROOT),encoder.encode(password),"Администратор","ADMIN");
 };}
}
