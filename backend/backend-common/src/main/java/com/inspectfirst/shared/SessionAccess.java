package com.inspectfirst.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class SessionAccess {
    public record Actor(String id, String email, String displayName, String role) {
        public boolean admin() { return "ADMIN".equals(role); }
    }
    private final RestClient identity;
    public SessionAccess(@Value("${services.identity:http://127.0.0.1:8087}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        identity = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public Actor require(HttpServletRequest request, boolean admin) {
        boolean write = !java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
        try {
            var call = identity.method(write ? HttpMethod.POST : HttpMethod.GET)
                .uri(write ? "/api/auth/authorize" : "/api/auth/me")
                .header(HttpHeaders.COOKIE, java.util.Objects.toString(request.getHeader("Cookie"),""));
            if (write) call.header("X-CSRF-TOKEN", java.util.Objects.toString(request.getHeader("X-CSRF-TOKEN"),""));
            Actor actor = call.retrieve().body(Actor.class);
            if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please log in.");
            if (admin && !actor.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required.");
            return actor;
        } catch (RestClientResponseException e) {
            int status=e.getStatusCode().value();
            throw new ResponseStatusException(status==401 ? HttpStatus.UNAUTHORIZED : status==403 ? HttpStatus.FORBIDDEN : HttpStatus.SERVICE_UNAVAILABLE,
                status==401 ? "Please log in." : status==403 ? "Access denied or expired security token." : "Identity service unavailable.");
        } catch (ResourceAccessException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Identity service unavailable."); }
    }
}
