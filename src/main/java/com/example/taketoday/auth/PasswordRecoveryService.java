package com.example.taketoday.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(noRollbackFor=ApiError.class)
public class PasswordRecoveryService {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final String from;
    private final SecureRandom random = new SecureRandom();
    public PasswordRecoveryService(JdbcTemplate db, PasswordEncoder encoder, JavaMailSender mail,
                                   @Value("${app.mail-from}") String from) {
        this.db=db; this.encoder=encoder; this.mail=mail; this.from=from;
    }
    public AuthModels.Challenge request(String email) {
        var accounts=db.queryForList("SELECT * FROM accounts WHERE LOWER(TRIM(email))=? FOR UPDATE", email.strip().toLowerCase(Locale.ROOT));
        if (accounts.isEmpty()) {
            throw new ApiError(BAD_REQUEST,"Учётная запись с такой почтой не найдена. Укажите адрес, использованный при регистрации.");
        }
        if (Boolean.TRUE.equals(accounts.get(0).get("blocked"))) {
            throw new ApiError(FORBIDDEN,"Учётная запись заблокирована. Восстановление пароля недоступно.");
        }
        UUID id=UUID.randomUUID();
        if (!accounts.isEmpty() && !Boolean.TRUE.equals(accounts.get(0).get("blocked"))) {
            var account=accounts.get(0);
            UUID owner=(UUID)account.get("id");
            var recent=db.queryForList("SELECT sent_at FROM password_recovery WHERE account_id=? ORDER BY sent_at DESC",owner);
            if (!recent.isEmpty() && instant(recent.get(0).get("sent_at")).plusSeconds(60).isAfter(Instant.now()))
                throw new ApiError(TOO_MANY_REQUESTS,"Повторная отправка доступна через 60 секунд.");
            db.update("UPDATE password_recovery SET consumed=TRUE,reset_token=NULL WHERE account_id=?",owner);
            String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
            db.update("INSERT INTO password_recovery(id,account_id,code_hash,expires_at,sent_at) VALUES (?,?,?,?,?)",
                    id,owner,encoder.encode(code),Timestamp.from(Instant.now().plusSeconds(600)),Timestamp.from(Instant.now()));
            var message=new SimpleMailMessage();
            message.setFrom(from); message.setTo((String)account.get("email"));
            message.setSubject("TakeToday — восстановление пароля");
            message.setText("Ваш код: "+code+"\nКод действует 10 минут. Никому не сообщайте его. Если вы не запрашивали восстановление, проигнорируйте письмо.");
            mail.send(message);
        }
        return new AuthModels.Challenge(id,600,60,"Код отправлен на почту вашей учётной записи.");
    }
    private Instant instant(Object value) {
        return value instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime)value).toInstant();
    }
    private Map<String,Object> challenge(UUID id) {
        // Lock the account first, consistently with request/reset, to serialize concurrent recovery requests.
        var owners=db.queryForList("SELECT account_id FROM password_recovery WHERE id=?",id);
        if (owners.isEmpty()) throw invalid();
        var accounts=db.queryForList("SELECT blocked FROM accounts WHERE id=? FOR UPDATE",owners.get(0).get("account_id"));
        if(accounts.isEmpty() || Boolean.TRUE.equals(accounts.get(0).get("blocked"))) throw invalid();
        var row=db.queryForMap("SELECT * FROM password_recovery WHERE id=? FOR UPDATE",id);
        if(Boolean.TRUE.equals(row.get("consumed")) || !instant(row.get("expires_at")).isAfter(Instant.now())
                || ((Number)row.get("attempts")).intValue()>=5) throw invalid();
        return row;
    }
    private ApiError invalid() { return new ApiError(BAD_REQUEST,"Код недействителен или истёк. Запросите новый код."); }
    public UUID verify(UUID id, String code) {
        var row=challenge(id);
        if(Boolean.TRUE.equals(row.get("verified"))) throw invalid();
        if(!encoder.matches(code,(String)row.get("code_hash"))) {
            db.update("UPDATE password_recovery SET attempts=attempts+1 WHERE id=?",id);
            throw new ApiError(BAD_REQUEST,"Неверный код. Доступно не более 5 попыток.");
        }
        UUID token=UUID.randomUUID();
        db.update("UPDATE password_recovery SET verified=TRUE,reset_token=? WHERE id=?",token,id);
        return token;
    }
    public void reset(UUID id, UUID token, String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiError(BAD_REQUEST,"Пароль должен занимать не более 72 байт UTF-8.");
        var row=challenge(id);
        if(!Boolean.TRUE.equals(row.get("verified")) || !token.equals(row.get("reset_token"))) throw invalid();
        Object owner=row.get("account_id");
        db.update("UPDATE accounts SET password_hash=?,verified=TRUE,auth_version=auth_version+1 WHERE id=?",encoder.encode(password),owner);
        db.update("UPDATE password_recovery SET consumed=TRUE,reset_token=NULL WHERE account_id=?",owner);
        db.update("UPDATE auth_challenges SET consumed=TRUE WHERE account_id=?",owner);
    }
}
