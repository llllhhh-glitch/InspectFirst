package com.inspectfirst.guide;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import com.fasterxml.jackson.databind.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuideApiTest {
    private static final String PRODUCT = "20000000-0000-0000-0000-000000000001";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test void searchFiltersAndPaginationAreApplied() throws Exception {
        var response = get("/api/products?q=sony&category=cameras&size=1");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("modelCode").asText()).isEqualTo("ILCE-6400");
        assertThat(mapper.readTree(get("/api/products?q=sony&category=audio").body()).get("items").size()).isZero();
        assertThat(mapper.readTree(get("/api/products?q=%25").body()).get("items").size()).isZero();
    }

    @Test void guideHasOrderedChecksAndTraceableSources() throws Exception {
        var response = get("/api/products/" + PRODUCT + "/guide");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        assertThat(body.get("items").size()).isEqualTo(3);
        assertThat(body.get("items").get(0).get("order").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(1).get("steps").size()).isEqualTo(2);
        for (JsonNode item : body.get("items")) {
            assertThat(item.get("sources").size()).isPositive();
            assertThat(item.get("sources").get(0).get("url").asText()).startsWith("https://helpguide.sony.net/");
        }
    }

    @Test void withdrawnGuideIsNeitherListedNorReadable() throws Exception {
        jdbc.update("UPDATE guide SET availability='WITHDRAWN' WHERE product_id=?", PRODUCT);
        try {
            assertThat(get("/api/products/" + PRODUCT + "/guide").statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(get("/api/products").body()).get("total").asInt()).isZero();
        } finally { jdbc.update("UPDATE guide SET availability='ACTIVE' WHERE product_id=?", PRODUCT); }
    }

    @Test void draftVersionNeverLeaksIntoPublicGuide() throws Exception {
        jdbc.update("UPDATE guide_version SET status='DRAFT'");
        try { assertThat(get("/api/products/" + PRODUCT + "/guide").statusCode()).isEqualTo(404); }
        finally { jdbc.update("UPDATE guide_version SET status='PUBLISHED'"); }
    }

    @Test void invalidAndMissingResourcesHaveControlledResponses() throws Exception {
        assertThat(get("/api/products?page=-1").statusCode()).isEqualTo(400);
        assertThat(get("/api/products?size=1000").statusCode()).isEqualTo(400);
        assertThat(get("/api/products?category=INVALID").statusCode()).isEqualTo(400);
        assertThat(get("/api/products/not-an-id/guide").statusCode()).isEqualTo(400);
        assertThat(get("/api/products/99999999-9999-9999-9999-999999999999/guide").statusCode()).isEqualTo(404);
    }
}
