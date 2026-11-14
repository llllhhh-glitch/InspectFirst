package com.inspectfirst.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptsTest {
    @Test void repeatedLoginAttemptsAreLimitedWithoutBlockingAnotherAddress() throws Exception {
        var filter=new SecurityConfig.Attempts();
        for (int i=0;i<31;i++) {
            var req=new MockHttpServletRequest("POST","/api/auth/login");
            req.setServletPath("/api/auth/login"); req.setRemoteAddr("192.0.2.1");
            req.setParameter("email","example@example.test"); req.setParameter("password","test-passphrase");
            var res=new MockHttpServletResponse();
            filter.doFilter(req,res,new MockFilterChain());
            assertEquals(i<30 ? 200 : 429,res.getStatus());
        }
        var req=new MockHttpServletRequest("POST","/api/auth/login");
        req.setServletPath("/api/auth/login"); req.setRemoteAddr("192.0.2.2");
        req.setParameter("email","example@example.test"); req.setParameter("password","test-passphrase");
        var res=new MockHttpServletResponse(); filter.doFilter(req,res,new MockFilterChain());
        assertEquals(200,res.getStatus());
    }
}
