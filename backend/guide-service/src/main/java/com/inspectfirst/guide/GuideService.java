package com.inspectfirst.guide;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class GuideService {
    private final com.inspectfirst.shared.Store db;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final GuideVersionRepository versions;

    public GuideService(CategoryRepository categories, ProductRepository products, GuideVersionRepository versions,com.inspectfirst.shared.Store db) {
        this.db=db;
        this.categories = categories;
        this.products = products;
        this.versions = versions;
    }

    public record CategoryDto(String id, String slug, String name) { }
    public record ProductDto(String id, String name, String brand, String modelCode, String category, String categorySlug) { }
    public record ProductPage(List<ProductDto> items, int page, int size, long total, boolean hasNext) { }
    public record SourceDto(String title, String url, String publisher) { }
    public record ItemDto(String id, int order, String title, String purpose, List<String> steps,
                          String expectedObservation, String abnormalSigns, String limitations, List<SourceDto> sources,List<java.util.Map<String,Object>> images) { }
    public record GuideDto(String id, String versionId, int version, ProductDto product, String title,
                           String scope, String tools, LocalDate reviewedOn, String contentNote, List<ItemDto> items) { }

    public List<CategoryDto> categories() {
        return categories.findAllByOrderByNameAsc().stream()
            .map(c -> new CategoryDto(c.getId(), c.getSlug(), c.getName())).toList();
    }

    public ProductPage search(String query, String category, int page, int size) {
        String escaped = query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var result = products.search("%" + escaped + "%", category, PageRequest.of(page, size, Sort.by("name").and(Sort.by("id"))));
        return new ProductPage(result.getContent().stream().map(this::productDto).toList(), page, size,
            result.getTotalElements(), result.hasNext());
    }

    public GuideDto guide(String productId) {
        var version = versions.findPublished(productId, PageRequest.of(0, 1)).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No published guide is available for this product."));
        return new GuideDto(version.getGuide().getId(), version.getId(), version.getVersionNo(),
            productDto(version.getGuide().getProduct()), version.getTitle(), "MODEL", version.getTools(),
            version.getReviewedOn(), version.getContentNote(), version.getItems().stream().map(i -> new ItemDto(
                i.getId(), i.getSortOrder(), i.getTitle(), i.getPurpose(), i.getMethod().lines().toList(),
                i.getExpectedObservation(), i.getAbnormalSigns(), i.getLimitations(),
                i.getSources().stream().map(s -> new SourceDto(s.getTitle(), s.getUrl(), s.getPublisher())).toList(),
                db.rows("SELECT url,alt,caption,kind,source_url,source_title FROM check_item_image WHERE check_item_id=? ORDER BY sort_order",i.getId())
            )).toList());
    }

    private ProductDto productDto(Product p) {
        return new ProductDto(p.getId(), p.getName(), p.getBrand(), p.getModelCode(), p.getCategory().getName(), p.getCategory().getSlug());
    }
}
