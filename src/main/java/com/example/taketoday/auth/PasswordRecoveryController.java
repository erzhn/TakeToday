package com.example.taketoday.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/recovery")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;
    public PasswordRecoveryController(PasswordRecoveryService service) { this.service = service; }
    public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email) {}
    public record Code(@NotNull UUID challengeId, @NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Password(@NotNull UUID challengeId, @NotNull UUID resetToken,
                           @NotBlank @Size(min=10,max=72) String password) {}
    @PostMapping("/request") public AuthModels.Challenge request(@Valid @RequestBody Email input) {
        return service.request(input.email());
    }
    @PostMapping("/verify") public Map<String,UUID> verify(@Valid @RequestBody Code input) {
        return Map.of("resetToken", service.verify(input.challengeId(), input.code()));
    }
    @PostMapping("/reset") public Map<String,String> reset(@Valid @RequestBody Password input) {
        service.reset(input.challengeId(), input.resetToken(), input.password());
        return Map.of("message", "Пароль изменён. Войдите с новым паролем.");
    }
}
