package com.inspectfirst.identity;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class Accounts implements UserDetailsService {
    private final JdbcTemplate db;
    public Accounts(JdbcTemplate db) { this.db = db; }
    static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    @Override public UserDetails loadUserByUsername(String email) {
        return db.query("SELECT id,email,display_name,password_hash,role,status FROM user_account WHERE email = ?",
            (rs, n) -> new Account(rs.getString("id"), rs.getString("email"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getString("role"), rs.getString("status")), normalize(email))
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    void create(String email, String name, String hash, String role) {
        db.update("INSERT INTO user_account (id,email,display_name,password_hash,role,admin_slot) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID().toString(), normalize(email), name.strip(), hash, role, role.equals("ADMIN") ? 1 : null);
    }
    boolean hasAdmin() { return db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role = 'ADMIN'", Long.class) > 0; }
    long userCount() { return db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role = 'USER'", Long.class); }
    public static final class Account extends User {
        private final String id, name, role;
        Account(String id, String email, String name, String hash, String role, String status) {
            super(email, hash, "ACTIVE".equals(status), true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            this.id = id; this.name = name; this.role = role;
        }
        UserView view() { return new UserView(id, getUsername(), name, role); }
    }
    public record UserView(String id, String email, String displayName, String role) {}
}
