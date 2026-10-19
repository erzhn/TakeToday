package com.example.taketoday.auth;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration
public class SecurityConfig {
 @Bean org.springframework.security.core.userdetails.UserDetailsService disabledPasswordOnlyLogin() { return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Используйте вход с email-кодом"); }; }
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
 @Bean SecurityFilterChain security(HttpSecurity http, SecurityContextRepository repository, org.springframework.jdbc.core.JdbcTemplate db) throws Exception {
 return http.addFilterAfter(new SessionGuard(db),org.springframework.security.web.context.SecurityContextHolderFilter.class).cors(c -> {}).securityContext(c -> c.securityContextRepository(repository))
 .authorizeHttpRequests(a -> a.requestMatchers("/", "/catalog", "/offers/*", "/images/**", "/index.html", "/forgot-password", "/styles.css", "/app.js", "/css/**", "/js/**", "/uploads/**", "/favicon.ico", "/api/auth/**", "/api/public/**", "/error").permitAll()
 .requestMatchers("/api/customer/**", "/api/orders/**").hasRole("CUSTOMER")
 .requestMatchers("/staff", "/staff/**", "/api/staff/**").hasRole("STAFF")
 .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
 .exceptionHandling(e -> e.authenticationEntryPoint((q,s,x)->s.sendError(401,"Войдите в учетную запись"))
 .accessDeniedHandler((q,s,x)->s.sendError(403,"Доступ запрещен или устарел защитный токен")))
 .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID").logoutSuccessHandler((q,s,a)->s.setStatus(204))).build();
 }
 @Bean CorsConfigurationSource cors(@Value("${app.frontend-origin}") String origin) {
 var c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));c.setAllowCredentials(true);
 c.setAllowedMethods(List.of("GET","POST","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));
 var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",c);return source;
 }
}
