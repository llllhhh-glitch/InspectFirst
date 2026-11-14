package com.inspectfirst.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final Accounts accounts;
    private final PasswordEncoder encoder;
    private final String email, password;
    public AdminBootstrap(Accounts accounts, PasswordEncoder encoder,
        @Value("${inspectfirst.admin.email:}") String email,
        @Value("${inspectfirst.admin.password:}") String password) {
        this.accounts = accounts; this.encoder = encoder; this.email = email; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        // A changed local configuration must never silently replace an existing administrator.
        if (accounts.hasAdmin()) return;
        if (!email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
            || email.length() > 254 || password.length() < 12 || password.length() > 128)
            throw new IllegalStateException("Initialize the single administrator using the local identity setup tool.");
        accounts.create(email, "Administrator", encoder.encode(password), "ADMIN");
    }
}
