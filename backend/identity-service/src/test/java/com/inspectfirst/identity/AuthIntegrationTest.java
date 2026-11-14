package com.inspectfirst.identity;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminBootstrap bootstrap;
    HttpClient browser;
    @BeforeEach void browser() {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path, String body, String contentType, boolean csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type",contentType);
        if (csrf) {
            var token = json.readTree(get("/api/auth/csrf").body());
            builder.header(token.get("headerName").asText(),token.get("token").asText());
        }
        return browser.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> register(String email, String password, String role) throws Exception {
        return post("/api/auth/register",json.writeValueAsString(Map.of("email",email,"password",password,"displayName","Student","role",role)),"application/json",true);
    }
    HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/auth/login","email="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),"application/x-www-form-urlencoded",true);
    }
    @Test void registrationLoginLogoutAndRoleCannotBeInjected() throws Exception {
        String email = "student-"+UUID.randomUUID()+"@example.test";
        assertEquals(201,register(email,"unique-student-passphrase","ADMIN").statusCode());
        String hash=db.queryForObject("SELECT password_hash FROM user_account WHERE email=?",String.class,email);
        assertNotEquals("unique-student-passphrase",hash); assertTrue(encoder.matches("unique-student-passphrase",hash));
        var login=login(email,"unique-student-passphrase"); assertEquals(200,login.statusCode());
        assertEquals("USER",json.readTree(login.body()).get("role").asText());
        assertFalse(login.body().contains("password"));
        assertEquals(200,get("/api/auth/me").statusCode());
        assertEquals(403,get("/api/ops/overview").statusCode());
        assertEquals(204,post("/api/auth/logout","","application/json",true).statusCode());
        assertEquals(401,get("/api/auth/me").statusCode());
        assertEquals(200,login(email,"unique-student-passphrase").statusCode());
    }
    @Test void multipleUsersAndCaseInsensitiveDuplicateEmail() throws Exception {
        String email="case-"+UUID.randomUUID()+"@example.test";
        assertEquals(201,register(email,"long-enough-passphrase","USER").statusCode());
        assertEquals(409,register(email.toUpperCase(Locale.ROOT),"other-long-passphrase","USER").statusCode());
        assertEquals(201,register("other-"+email,"another-user-passphrase","USER").statusCode());
        assertEquals(200,login(email.toUpperCase(Locale.ROOT),"long-enough-passphrase").statusCode());
    }
    @Test void invalidPasswordCsrfAndAnonymousAccess() throws Exception {
        assertEquals(401,get("/api/auth/me").statusCode());
        assertEquals(401,get("/api/ops/overview").statusCode());
        assertEquals(403,post("/api/auth/register","{}","application/json",false).statusCode());
        assertEquals(400,register("bad@example.test","short","USER").statusCode());
        assertEquals(401,login("missing@example.test","incorrect-passphrase").statusCode());
        assertEquals(401,login("admin@example.test","incorrect-passphrase").statusCode());
    }
    @Test void singleAdministratorAndDatabaseConstraint() throws Exception {
        bootstrap.run(null);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role='ADMIN'",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            db.update("INSERT INTO user_account(id,email,display_name,password_hash,role,admin_slot) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(),"second@example.test","Other","not-a-real-hash","ADMIN",1));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            db.update("INSERT INTO user_account(id,email,display_name,password_hash,role,admin_slot) VALUES (?,?,?,?,?,NULL)",
                UUID.randomUUID().toString(),"null-slot@example.test","Other","not-a-real-hash","ADMIN"));
        assertEquals(200,login("admin@example.test","test-only-admin-passphrase").statusCode());
        assertEquals(200,get("/api/ops/overview").statusCode());
        assertEquals("ADMIN",json.readTree(get("/api/auth/me").body()).get("role").asText());
        assertEquals(409,register("admin@example.test","some-other-passphrase","USER").statusCode());
    }
    @Test void loginRotatesSessionAndLogoutRevokesOldCookie() throws Exception {
        var token=get("/api/auth/csrf");
        var oldCsrf=json.readTree(token.body());
        String oldCookie=token.headers().allValues("set-cookie").stream().filter(s->s.startsWith("INSPECTFIRST_SESSION=")).findFirst().orElseThrow().split(";")[0];
        var login=login("admin@example.test","test-only-admin-passphrase");
        String loggedCookie=login.headers().allValues("set-cookie").stream().filter(s->s.startsWith("INSPECTFIRST_SESSION=")).findFirst().orElseThrow().split(";")[0];
        assertNotEquals(oldCookie,loggedCookie);
        var stale=browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/logout"))
            .header(oldCsrf.get("headerName").asText(),oldCsrf.get("token").asText())
            .POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,stale.statusCode());
        post("/api/auth/logout","","application/json",true);
        var replay=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me")).header("Cookie",loggedCookie).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,replay.statusCode());
    }
    @Test void disablingUserRevokesActiveSessionAndRejectsLogin() throws Exception {
        String email="disabled-"+UUID.randomUUID()+"@example.test";
        assertEquals(201,register(email,"disabled-test-passphrase","USER").statusCode());
        var logged=login(email,"disabled-test-passphrase");String userId=json.readTree(logged.body()).get("id").asText();
        HttpClient studentBrowser=browser;browser();
        login("admin@example.test","test-only-admin-passphrase");
        var token=json.readTree(get("/api/auth/csrf").body());
        var response=browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/ops/users/"+userId+"/status"))
            .header("Content-Type","application/json").header(token.get("headerName").asText(),token.get("token").asText())
            .method("PATCH",HttpRequest.BodyPublishers.ofString("{\"status\":\"DISABLED\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());
        var stale=studentBrowser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,stale.statusCode());
        assertEquals(401,login(email,"disabled-test-passphrase").statusCode());
    }
}
