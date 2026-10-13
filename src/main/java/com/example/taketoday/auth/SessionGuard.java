package com.example.taketoday.auth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class SessionGuard extends OncePerRequestFilter {
 private final JdbcTemplate db;
 public SessionGuard(JdbcTemplate db){this.db=db;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
 var auth=SecurityContextHolder.getContext().getAuthentication();
 if(auth!=null && auth.getPrincipal() instanceof AuthModels.User u){
 var rows=db.queryForList("SELECT blocked,auth_version FROM accounts WHERE id=?",u.id());
 if(rows.isEmpty()||Boolean.TRUE.equals(rows.get(0).get("blocked"))||((Number)rows.get(0).get("auth_version")).intValue()!=u.authVersion()){
 SecurityContextHolder.clearContext();if(request.getSession(false)!=null)request.getSession(false).invalidate();
 }
 }
 chain.doFilter(request,response);
 }
}
