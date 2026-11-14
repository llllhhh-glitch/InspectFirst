package com.inspectfirst.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;

class ActiveAccountFilter extends OncePerRequestFilter {
    private final Accounts accounts;
    ActiveAccountFilter(Accounts accounts) { this.accounts=accounts; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof Accounts.Account account) {
            if(!accounts.loadUserByUsername(account.getUsername()).isEnabled()) {
                if(req.getSession(false)!=null) req.getSession(false).invalidate();
                SecurityContextHolder.clearContext(); SecurityConfig.error(res,401,"This account has been disabled."); return;
            }
        }
        chain.doFilter(req,res);
    }
}
