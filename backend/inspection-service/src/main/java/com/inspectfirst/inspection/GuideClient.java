package com.inspectfirst.inspection;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class GuideClient {
    private final RestClient client;
    public GuideClient(@Value("${services.guide:http://127.0.0.1:8086}") String url) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(5));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    JsonNode snapshot(String product,String category) {
        try { return client.get().uri(product!=null?"/api/products/{id}/guide":"/api/categories/{id}/guide",product!=null?product:category).retrieve().body(JsonNode.class); }
        catch(RestClientResponseException e) { throw new ResponseStatusException(e.getStatusCode().value()==404?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,"The selected guide is unavailable. Refresh before starting."); }
        catch(ResourceAccessException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Guide service unavailable. No inspection was created."); }
    }
}
