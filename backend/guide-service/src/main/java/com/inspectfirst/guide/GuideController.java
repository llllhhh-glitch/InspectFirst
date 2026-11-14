package com.inspectfirst.guide;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class GuideController {
    private final GuideService service;
    public GuideController(GuideService service) { this.service = service; }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "guide-service"); }

    @GetMapping("/categories")
    public List<GuideService.CategoryDto> categories() { return service.categories(); }

    @GetMapping("/products")
    public GuideService.ProductPage products(@RequestParam(defaultValue = "") @Size(max = 100) String q,
        @RequestParam(defaultValue = "") @Pattern(regexp = "[a-z-]{0,60}") String category,
        @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
        @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size) {
        return service.search(q, category, page, size);
    }

    @GetMapping("/products/{id}/guide")
    public GuideService.GuideDto guide(@PathVariable @Pattern(regexp = "[a-f0-9-]{36}") String id) {
        return service.guide(id);
    }
}
