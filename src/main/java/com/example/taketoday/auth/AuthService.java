package com.example.taketoday.auth;
import static com.example.taketoday.auth.AuthModels.*;
import static org.springframework.http.HttpStatus.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
@Service
@Transactional(noRollbackFor=ApiError.class)
public class AuthService {
 private final JdbcTemplate db; private final PasswordEncoder encoder; private final JavaMailSender mail; private final String from;
 private final SecureRandom random=new SecureRandom(); private final String dummy;
 public AuthService(JdbcTemplate db,PasswordEncoder encoder,JavaMailSender mail,@Value("${app.mail-from}") String from){this.db=db;this.encoder=encoder;this.mail=mail;this.from=from;dummy=encoder.encode(UUID.randomUUID().toString());}
 private String email(String s){return s.strip().toLowerCase(Locale.ROOT);}
 private Timestamp now(){return Timestamp.from(Instant.now());}
 private Timestamp after(long seconds){return Timestamp.from(Instant.now().plusSeconds(seconds));}
 private boolean past(Object v){return ((java.time.OffsetDateTime) convertTime(v)).toInstant().isBefore(Instant.now());}
 private Object convertTime(Object v){if(v instanceof Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);return v;}
 private Instant instant(Object v){return ((OffsetDateTime)convertTime(v)).toInstant();}
 private void password(String p){if(p.getBytes(StandardCharsets.UTF_8).length>72)throw new ApiError(BAD_REQUEST,"Пароль должен занимать не более 72 байт UTF-8.");}
 private Map<String,Object> account(UUID id){return db.queryForMap("SELECT * FROM accounts WHERE id=? FOR UPDATE",id);}
 private Map<String,Object> byEmail(String email){var rows=db.queryForList("SELECT * FROM accounts WHERE email=? FOR UPDATE",email);return rows.isEmpty()?null:rows.get(0);}
 private void throttle(String email){
 db.update("INSERT INTO auth_throttle(email,attempts,window_start) VALUES (?,0,?) ON CONFLICT DO NOTHING",email,now());
 var row=db.queryForMap("SELECT * FROM auth_throttle WHERE email=? FOR UPDATE",email);
 if(instant(row.get("window_start")).plusSeconds(900).isBefore(Instant.now()))db.update("UPDATE auth_throttle SET attempts=1,window_start=? WHERE email=?",now(),email);
 else {if(((Number)row.get("attempts")).intValue()>=10)throw new ApiError(TOO_MANY_REQUESTS,"Слишком много попыток. Подождите 15 минут.");db.update("UPDATE auth_throttle SET attempts=attempts+1 WHERE email=?",email);}
 }
 public Challenge register(Registration r){
 String e=email(r.email()); throttle(e);password(r.password());
 if("STAFF".equals(r.role()) && (blank(r.establishmentName())||blank(r.address())||blank(r.contact())||blank(r.workingHours())))throw new ApiError(BAD_REQUEST,"Укажите название, адрес, контакты и часы работы заведения.");
 var existing=byEmail(e);if(existing!=null)throw new ApiError(CONFLICT,"Эта почта уже зарегистрирована. Войдите или восстановите пароль.");
 UUID id=UUID.randomUUID();db.update("INSERT INTO accounts(id,email,password_hash,name,role) VALUES (?,?,?,?,?)",id,e,encoder.encode(r.password()),r.name().strip(),r.role());
 if("STAFF".equals(r.role()))db.update("INSERT INTO establishments(id,owner_id,name,address,contact,working_hours) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,r.establishmentName().strip(),r.address().strip(),r.contact().strip(),r.workingHours().strip());
 return issue(id,e,"REGISTER");
 }
 private boolean blank(String s){return s==null||s.isBlank();}
 public Challenge login(Login r){password(r.password());String e=email(r.email());throttle(e);var a=byEmail(e);
 boolean valid=encoder.matches(r.password(),a==null?dummy:(String)a.get("password_hash"));
 if(a==null||!valid||Boolean.TRUE.equals(a.get("blocked")))throw new ApiError(UNAUTHORIZED,"Неверная почта или пароль либо учетная запись заблокирована.");
 return issue((UUID)a.get("id"),e,Boolean.TRUE.equals(a.get("verified"))?"LOGIN":"REGISTER");
 }
 public Challenge forgot(EmailRequest r){String e=email(r.email());throttle(e);var a=byEmail(e);
 if(a!=null&&!Boolean.TRUE.equals(a.get("blocked")))return issue((UUID)a.get("id"),e,"RESET");
 return new Challenge(UUID.randomUUID(),600,60,"Если учетная запись существует, письмо с кодом отправлено.");
 }
 private Challenge issue(UUID id,String e,String purpose){
 var recent=db.queryForList("SELECT sent_at FROM auth_challenges WHERE account_id=? ORDER BY sent_at DESC",id);
 if(!recent.isEmpty()&&instant(recent.get(0).get("sent_at")).plusSeconds(60).isAfter(Instant.now()))throw new ApiError(TOO_MANY_REQUESTS,"Следующее письмо можно запросить через 60 секунд.");
 db.update("UPDATE auth_challenges SET consumed=TRUE WHERE account_id=?",id);
 UUID challenge=UUID.randomUUID();String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
 db.update("INSERT INTO auth_challenges(id,account_id,purpose,code_hash,expires_at,sent_at) VALUES (?,?,?,?,?,?)",challenge,id,purpose,encoder.encode(code),after(600),now());
 send(e,code,purpose);return new Challenge(challenge,600,60,"RESET".equals(purpose)?"Если учетная запись существует, письмо с кодом отправлено.":"Код отправлен на вашу электронную почту.");
 }
 private void send(String e,String code,String purpose){var m=new SimpleMailMessage();m.setFrom(from);m.setTo(e);m.setSubject("TakeToday — "+("RESET".equals(purpose)?"восстановление доступа":"код подтверждения"));m.setText("Ваш код: "+code+"\nОн действует 10 минут. Никому не сообщайте код. Если вы не запрашивали письмо, проигнорируйте его.");mail.send(m);}
 public Challenge resend(UUID id){var info=db.queryForList("SELECT a.email FROM accounts a JOIN auth_challenges c ON a.id=c.account_id WHERE c.id=?",id);if(!info.isEmpty())throttle((String)info.get(0).get("email"));var row=challenge(id);UUID user=(UUID)row.get("account_id");var a=account(user);if(Boolean.TRUE.equals(a.get("blocked")))throw new ApiError(FORBIDDEN,"Учетная запись заблокирована.");return issue(user,(String)a.get("email"),(String)row.get("purpose"));}
 private Map<String,Object> challenge(UUID id){var owners=db.queryForList("SELECT account_id FROM auth_challenges WHERE id=?",id);if(!owners.isEmpty())account((UUID)owners.get(0).get("account_id"));var rows=db.queryForList("SELECT * FROM auth_challenges WHERE id=? FOR UPDATE",id);
 if(rows.isEmpty())throw new ApiError(BAD_REQUEST,"Код недействителен. Запросите новый.");var c=rows.get(0);
 if(Boolean.TRUE.equals(c.get("consumed"))||past(c.get("expires_at"))||((Number)c.get("attempts")).intValue()>=5)throw new ApiError(BAD_REQUEST,"Код истек или попытки исчерпаны. Начните заново.");return c;
 }
 private Map<String,Object> verify(UUID id,String code,boolean reset){var c=challenge(id);
 if(reset!= "RESET".equals(c.get("purpose")))throw new ApiError(BAD_REQUEST,"Код предназначен для другого действия.");
 if(!encoder.matches(code,(String)c.get("code_hash"))){db.update("UPDATE auth_challenges SET attempts=attempts+1 WHERE id=?",id);throw new ApiError(BAD_REQUEST,"Неверный код. Доступно не более 5 попыток.");}
 var a=account((UUID)c.get("account_id"));if(Boolean.TRUE.equals(a.get("blocked")))throw new ApiError(FORBIDDEN,"Учетная запись заблокирована.");
 db.update("UPDATE auth_challenges SET consumed=TRUE WHERE id=?",id);return a;
 }
 public User complete(Verification r){var info=db.queryForList("SELECT a.email FROM accounts a JOIN auth_challenges c ON a.id=c.account_id WHERE c.id=?",r.challengeId());if(!info.isEmpty())db.queryForList("SELECT email FROM auth_throttle WHERE email=? FOR UPDATE",info.get(0).get("email"));var a=verify(r.challengeId(),r.code(),false);db.update("UPDATE accounts SET verified=TRUE WHERE id=?",a.get("id"));db.update("UPDATE auth_throttle SET attempts=0,window_start=? WHERE email=?",now(),a.get("email"));return user(a);}
 public void reset(Reset r){password(r.password());var a=verify(r.challengeId(),r.code(),true);db.update("UPDATE accounts SET password_hash=?,verified=TRUE,auth_version=auth_version+1 WHERE id=?",encoder.encode(r.password()),a.get("id"));db.update("UPDATE auth_challenges SET consumed=TRUE WHERE account_id=?",a.get("id"));}
 public User current(UUID id){var a=account(id);if(Boolean.TRUE.equals(a.get("blocked")))throw new ApiError(FORBIDDEN,"Учетная запись заблокирована.");return user(a);}
 private User user(Map<String,Object> a){return new User((UUID)a.get("id"),(String)a.get("email"),(String)a.get("name"),(String)a.get("role"),((Number)a.get("auth_version")).intValue());}
 public Map<String,Object> establishment(UUID owner){var rows=db.queryForList("SELECT id,name,address,contact,working_hours FROM establishments WHERE owner_id=?",owner);if(rows.isEmpty())throw new ApiError(NOT_FOUND,"Заведение не найдено.");var row=rows.get(0);return Map.of("id",row.get("id"),"name",row.get("name"),"address",row.get("address"),"contact",row.get("contact"),"working_hours",row.get("working_hours"));}
}
