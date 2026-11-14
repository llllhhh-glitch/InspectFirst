package com.inspectfirst.identity;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final Accounts accounts;
    private final PasswordEncoder encoder;
    public AuthController(Accounts accounts, PasswordEncoder encoder) { this.accounts = accounts; this.encoder = encoder; }
    record Registration(
        @NotBlank @Email @Size(max=254) @Pattern(regexp="[\\x21-\\x7E]+") String email,
        @NotBlank @Size(max=60) String displayName,
        @NotNull @Size(min=12, max=128) String password) {}
    @GetMapping("/api/auth/csrf") Map<String,String> csrf(CsrfToken csrf) {
        return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
    }
    @PostMapping("/api/auth/register") ResponseEntity<?> register(@Valid @RequestBody Registration input) {
        try { accounts.create(input.email(), input.displayName(), encoder.encode(input.password()), "USER"); }
        catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).body(Map.of("detail", "This email is already registered. Please log in."));
        }
        return ResponseEntity.status(201).body(Map.of("message", "Account created. You can now log in."));
    }
    @GetMapping("/api/auth/me") Accounts.UserView me(@AuthenticationPrincipal Accounts.Account account) { return account.view(); }
    @PostMapping("/api/auth/authorize") Accounts.UserView authorize(@AuthenticationPrincipal Accounts.Account account) { return account.view(); }
    @GetMapping("/api/auth/health") Map<String,String> health() { return Map.of("status", "UP", "service", "identity-service"); }
    @GetMapping("/api/ops/overview") Map<String,Object> overview() {
        return Map.of("registeredUsers", accounts.userCount(), "administratorAccounts", 1);
    }
}
