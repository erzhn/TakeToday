package com.example.taketoday.auth;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class AuthModels {
 private AuthModels() {}
 public record Registration(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=10,max=72) String password,
 @NotBlank @Size(max=100) String name, @NotBlank @Pattern(regexp="CUSTOMER|STAFF") String role,
 @Size(max=150) String establishmentName, @Size(max=300) String address, @Size(max=150) String contact, @Size(max=150) String workingHours) {}
 public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
 public record EmailRequest(@NotBlank @Email @Size(max=254) String email) {}
 public record Verification(@NotNull UUID challengeId, @NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
 public record Resend(@NotNull UUID challengeId) {}
 public record Reset(@NotNull UUID challengeId, @NotBlank @Pattern(regexp="[0-9]{6}") String code, @NotBlank @Size(min=10,max=72) String password) {}
 public record Challenge(UUID challengeId, int expiresIn, int resendAfter, String message) {}
 public record User(UUID id, String email, String name, String role, int authVersion) implements java.io.Serializable {}
}
