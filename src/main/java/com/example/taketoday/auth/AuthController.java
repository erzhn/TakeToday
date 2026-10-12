package com.example.taketoday.auth;
import static com.example.taketoday.auth.AuthModels.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final AuthService service;private final SecurityContextRepository repository;
 public AuthController(AuthService service,SecurityContextRepository repository){this.service=service;this.repository=repository;}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @PostMapping("/register") Challenge register(@Valid @RequestBody Registration r){return service.register(r);}
 @PostMapping("/login") Challenge login(@Valid @RequestBody Login r){return service.login(r);}
 @PostMapping("/forgot-password") Challenge forgot(@Valid @RequestBody EmailRequest r){return service.forgot(r);}
 @PostMapping("/resend") Challenge resend(@Valid @RequestBody Resend r){return service.resend(r.challengeId());}
 @PostMapping("/verify") User verify(@Valid @RequestBody Verification r,HttpServletRequest request,HttpServletResponse response){
 User u=service.complete(r);if(request.getSession(false)!=null)request.getSession(false).invalidate();request.getSession(true);
 var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u,null,List.of(new SimpleGrantedAuthority("ROLE_"+u.role()))));
 SecurityContextHolder.setContext(context);repository.saveContext(context,request,response);return u;
 }
 @PostMapping("/reset-password") Map<String,String> reset(@Valid @RequestBody Reset r){service.reset(r);return Map.of("message","Пароль изменен. Войдите с новым паролем.");}
 @GetMapping("/me") Object me(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof User u))return Map.of("role","GUEST","name","Гость");return service.current(u.id());}
}
