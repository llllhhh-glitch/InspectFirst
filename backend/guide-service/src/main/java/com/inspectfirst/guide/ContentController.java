package com.inspectfirst.guide;

import com.inspectfirst.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ContentController {
    private final ContentService service; private final SessionAccess auth;private final Store db;
    public ContentController(ContentService service,SessionAccess auth,Store db) { this.service=service;this.auth=auth;this.db=db; }
    record Publish(@Min(0) long expectedVersion) {}
    record Withdraw(@NotBlank @Size(max=600) String reason) {}
    @GetMapping("/api/ops/content/stats") Object stats(HttpServletRequest r) { auth.require(r,true);return Map.of("products",db.count("SELECT COUNT(*) FROM product"),"guides",db.count("SELECT COUNT(*) FROM guide"),"openFeedback",db.count("SELECT COUNT(*) FROM feedback WHERE status NOT IN ('RESOLVED','CLOSED')")); }
    @GetMapping("/api/categories/{id}/guide") Object categoryGuide(@PathVariable String id) { return service.categoryGuide(id); }
    @GetMapping("/api/guides/versions/{id}") Object publicVersion(@PathVariable String id) { return service.version(id,true); }
    @GetMapping("/api/ops/content/categories") Object categories(HttpServletRequest r) { auth.require(r,true); return db.rows("SELECT * FROM category ORDER BY name,id"); }
    @PostMapping("/api/ops/content/categories") Object addCategory(@Valid @RequestBody ContentService.CategoryInput input,HttpServletRequest r) { return service.category(null,input,auth.require(r,true).id()); }
    @PutMapping("/api/ops/content/categories/{id}") Object editCategory(@PathVariable String id,@Valid @RequestBody ContentService.CategoryInput input,HttpServletRequest r) { return service.category(id,input,auth.require(r,true).id()); }
    @GetMapping("/api/ops/content/products") Object products(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { auth.require(r,true);return service.products(q,page,size); }
    @PostMapping("/api/ops/content/products") Object addProduct(@Valid @RequestBody ContentService.ProductInput input,HttpServletRequest r) { return service.product(null,input,auth.require(r,true).id()); }
    @PutMapping("/api/ops/content/products/{id}") Object editProduct(@PathVariable String id,@Valid @RequestBody ContentService.ProductInput input,HttpServletRequest r) { return service.product(id,input,auth.require(r,true).id()); }
    @GetMapping("/api/ops/content/guides") Object guides(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { auth.require(r,true);return service.guides(page,size); }
    @PostMapping("/api/ops/content/guides") Object draft(@RequestBody ContentService.NewGuide input,HttpServletRequest r) { return service.draft(input,auth.require(r,true).id()); }
    @GetMapping("/api/ops/content/guides/{id}/versions") Object versions(@PathVariable String id,HttpServletRequest r) { auth.require(r,true);return db.rows("SELECT * FROM guide_version WHERE guide_id=? ORDER BY version_no DESC",id); }
    @GetMapping("/api/ops/content/versions/{id}") Object version(@PathVariable String id,HttpServletRequest r) { auth.require(r,true);return service.version(id,false); }
    @PutMapping("/api/ops/content/versions/{id}") Object save(@PathVariable String id,@Valid @RequestBody ContentService.Draft input,HttpServletRequest r) { return service.save(id,input,auth.require(r,true).id()); }
    @PostMapping("/api/ops/content/versions/{id}/publish") Object publish(@PathVariable String id,@Valid @RequestBody Publish input,HttpServletRequest r) { return service.publish(id,input.expectedVersion(),auth.require(r,true).id()); }
    @PostMapping("/api/ops/content/guides/{id}/withdraw") Object withdraw(@PathVariable String id,@Valid @RequestBody Withdraw input,HttpServletRequest r) { service.withdraw(id,input.reason(),auth.require(r,true).id());return Map.of("status","WITHDRAWN"); }
    @GetMapping("/api/ops/content/audit") Object audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { auth.require(r,true);int offset=Store.offset(page,size);return db.page(db.rows("SELECT * FROM audit_log ORDER BY created_at DESC,id LIMIT ? OFFSET ?",size,offset),db.count("SELECT COUNT(*) FROM audit_log"),page,size); }
}
