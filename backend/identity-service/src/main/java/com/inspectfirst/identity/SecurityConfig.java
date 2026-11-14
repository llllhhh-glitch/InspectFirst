package com.inspectfirst.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"detail\":\"" + message + "\"}");
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, Accounts accounts) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/health").permitAll()
                .requestMatchers("/api/ops/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/me", "/api/auth/authorize").authenticated()
                .anyRequest().denyAll())
            .requestCache(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> error(res,401,"Please log in to continue."))
                .accessDeniedHandler((req,res,ex) -> error(res,403,"Access denied. Refresh the page if your session has expired.")))
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((req,res,auth) -> {
                    res.setContentType("application/json");
                    json.writeValue(res.getWriter(), ((Accounts.Account) auth.getPrincipal()).view());
                })
                .failureHandler((req,res,ex) -> error(res,401,"Email or password is incorrect.")))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("INSPECTFIRST_SESSION")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .addFilterAfter(new ActiveAccountFilter(accounts), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new Attempts(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    // Bounded, single-instance abuse protection. Reverse-proxy headers are deliberately not trusted.
    static class Attempts extends OncePerRequestFilter {
        record Window(long since, int count) {}
        private final Map<String,Window> windows = new HashMap<>();
        private synchronized boolean allow(String key, int max) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(e -> now - e.getValue().since() >= 600_000);
            Window old = windows.get(key);
            if (old == null && windows.size() >= 10_000) return false;
            Window next = old == null ? new Window(now,1) : new Window(old.since(),old.count()+1);
            windows.put(key,next); return next.count() <= max;
        }
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
            String path = req.getServletPath();
            if (req.getMethod().equals("POST") && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
                if (!allow(req.getRemoteAddr()+path, path.endsWith("login") ? 30 : 10)) {
                    res.setHeader("Retry-After", "600"); error(res,429,"Too many attempts. Please try again in 10 minutes."); return;
                }
                if (path.endsWith("login")) {
                    String email = req.getParameter("email"), password = req.getParameter("password");
                    if (email == null || password == null || email.length() > 254 || password.length() > 128) {
                        error(res,401,"Email or password is incorrect."); return;
                    }
                }
            }
            chain.doFilter(req,res);
        }
    }
}
