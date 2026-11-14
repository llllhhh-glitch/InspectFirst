package com.inspectfirst.guide;

import com.inspectfirst.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.net.URI;
import java.util.*;
import static com.inspectfirst.shared.Store.*;

@Service
public class ContentService {
    final Store db;
    public ContentService(Store db) { this.db=db; }
    public record CategoryInput(@NotBlank @Pattern(regexp="[a-z-]{1,60}") String slug,@NotBlank @Size(max=80) String name) {}
    public record ProductInput(@NotBlank String categoryId,@NotBlank @Size(max=80) String brand,@NotBlank @Size(max=80) String modelCode,
        @NotBlank @Size(max=160) String name,@NotNull @Pattern(regexp="ACTIVE|ARCHIVED") String status) {}
    public record NewGuide(String productId,String categoryId) {}
    public record Source(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=1000) String url,@NotBlank @Size(max=80) String publisher) {}
    public record Illustration(@NotBlank @Size(max=1000) String url,@NotBlank @Size(max=400) String alt,
        @NotBlank @Size(max=600) String caption,@NotBlank @Pattern(regexp="DIAGRAM|PHOTO|SCREENSHOT") String kind,
        @NotBlank @Size(max=1000) String sourceUrl,@NotBlank @Size(max=200) String sourceTitle) {}
    public record Item(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=600) String purpose,
        @NotEmpty @Size(max=20) List<@NotBlank @Size(max=500) String> steps,
        @NotBlank @Size(max=1200) String expectedObservation,@NotBlank @Size(max=1200) String abnormalSigns,
        @NotBlank @Size(max=1200) String limitations,@NotEmpty @Size(max=10) List<@Valid Source> sources,@Size(max=3) List<@Valid Illustration> images) {
        public Item(String title,String purpose,List<String> steps,String expectedObservation,String abnormalSigns,String limitations,List<Source> sources) {
            this(title,purpose,steps,expectedObservation,abnormalSigns,limitations,sources,List.of());
        }
    }
    public record Draft(@Min(0) long expectedVersion,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=600) String tools,
        @NotNull @PastOrPresent LocalDate reviewedOn,@NotBlank @Size(max=1200) String contentNote,
        @NotEmpty @Size(max=40) List<@Valid Item> items) {}
    @Transactional public Map<String,Object> category(String id,CategoryInput in,String actor) {
        if(id==null) { id=id(); db.update("INSERT INTO category(id,slug,name) VALUES (?,?,?)",id,in.slug(),in.name().strip()); }
        else { db.one("SELECT id FROM category WHERE id=?",id); db.update("UPDATE category SET slug=?,name=? WHERE id=?",in.slug(),in.name().strip(),id); }
        db.audit(actor,"CATEGORY_SAVE",id,Map.of()); return db.one("SELECT * FROM category WHERE id=?",id);
    }
    @Transactional public Map<String,Object> product(String id,ProductInput in,String actor) {
        db.one("SELECT id FROM category WHERE id=?",in.categoryId());
        if(id==null) { id=id(); db.update("INSERT INTO product(id,category_id,brand,model_code,name,status) VALUES (?,?,?,?,?,?)",id,in.categoryId(),in.brand().strip(),in.modelCode().strip(),in.name().strip(),in.status()); }
        else { db.one("SELECT id FROM product WHERE id=?",id); db.update("UPDATE product SET category_id=?,brand=?,model_code=?,name=?,status=? WHERE id=?",in.categoryId(),in.brand().strip(),in.modelCode().strip(),in.name().strip(),in.status(),id); }
        db.audit(actor,"PRODUCT_SAVE",id,Map.of("status",in.status())); return db.one("SELECT * FROM product WHERE id=?",id);
    }
    public Map<String,Object> products(String q,int page,int size) {
        int offset=offset(page,size); String term=like(q);
        String where=" WHERE LOWER(name) LIKE ? ESCAPE '!' OR LOWER(model_code) LIKE ? ESCAPE '!' OR LOWER(brand) LIKE ? ESCAPE '!'";
        return db.page(db.rows("SELECT * FROM product"+where+" ORDER BY name,id LIMIT ? OFFSET ?",term,term,term,size,offset),db.count("SELECT COUNT(*) FROM product"+where,term,term,term),page,size);
    }
    public Map<String,Object> guides(int page,int size) {
        int offset=offset(page,size);
        return db.page(db.rows("SELECT g.*,COALESCE(p.name,c.name) AS display_name FROM guide g LEFT JOIN product p ON p.id=g.product_id LEFT JOIN category c ON c.id=g.category_id ORDER BY g.id LIMIT ? OFFSET ?",size,offset),db.count("SELECT COUNT(*) FROM guide"),page,size);
    }
    @Transactional public Map<String,Object> draft(NewGuide in,String actor) {
        boolean model=in.productId()!=null&&!in.productId().isBlank(), category=in.categoryId()!=null&&!in.categoryId().isBlank();
        check(model!=category,400,"Choose exactly one product or category.");
        if(model) db.one("SELECT id FROM product WHERE id=?",in.productId()); else db.one("SELECT id FROM category WHERE id=?",in.categoryId());
        // Lock the catalogue parent even before a guide exists, serializing first-draft creation.
        db.one(model?"SELECT id FROM product WHERE id=? FOR UPDATE":"SELECT id FROM category WHERE id=? FOR UPDATE",model?in.productId():in.categoryId());
        var existing=db.rows("SELECT * FROM guide WHERE "+(model?"product_id":"category_id")+"=?",model?in.productId():in.categoryId());
        String guideId;
        if(existing.isEmpty()) {
            guideId=id(); db.update("INSERT INTO guide(id,product_id,category_id,availability) VALUES (?,?,?,'DRAFT')",guideId,model?in.productId():null,category?in.categoryId():null);
        } else guideId=existing.get(0).get("id").toString();
        db.one("SELECT id FROM guide WHERE id=? FOR UPDATE",guideId);
        check(db.count("SELECT COUNT(*) FROM guide_version WHERE guide_id=? AND status='DRAFT'",guideId)==0,409,"Edit the existing draft first.");
        int number=(int)db.count("SELECT COALESCE(MAX(version_no),0) FROM guide_version WHERE guide_id=?",guideId)+1;
        String version=id();
        db.update("INSERT INTO guide_version(id,guide_id,version_no,status,title,tools,reviewed_on,content_note) VALUES (?,?,?,'DRAFT','Untitled draft','',?,'')",version,guideId,number,LocalDate.now());
        db.audit(actor,"GUIDE_DRAFT",version,Map.of("guideId",guideId)); return version(version,false);
    }
    @Transactional public Map<String,Object> save(String id,Draft in,String actor) {
        var version=db.one("SELECT * FROM guide_version WHERE id=? FOR UPDATE",id);
        check(version.get("status").equals("DRAFT"),409,"Published versions are immutable. Create a new draft.");
        check(((Number)version.get("editVersion")).longValue()==in.expectedVersion(),409,"The draft changed. Refresh before saving.");
        for(Item item:in.items()) {
            check(String.join("\n",item.steps()).length()<=2000,400,"Check instructions are too long.");
            if(item.images()!=null) for(Illustration image:item.images()) {
                check(image.url()!=null&&image.url().matches("/images/checks/[a-z0-9-]+\\.(svg|png|jpg|jpeg|webp)"),400,"Use a local inspection example image.");
                URI reference;try { reference=URI.create(image.sourceUrl()); } catch(Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid image reference."); }
                check("https".equalsIgnoreCase(reference.getScheme())&&reference.getHost()!=null&&reference.getUserInfo()==null,400,"Image references require HTTPS without credentials.");
                check(Set.of("DIAGRAM","PHOTO","SCREENSHOT").contains(image.kind()),400,"Invalid example kind.");
            }
            for(Source source:item.sources()) {
                URI uri; try { uri=URI.create(source.url()); } catch(Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid source URL."); }
                check(Set.of("http","https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT))&&uri.getHost()!=null&&uri.getUserInfo()==null,400,"Sources require an HTTP(S) URL without credentials.");
            }
        }
        db.update("DELETE FROM check_item_source WHERE check_item_id IN (SELECT id FROM check_item WHERE guide_version_id=?)",id);
        db.update("DELETE FROM check_item WHERE guide_version_id=?",id);
        int order=0;
        for(Item item:in.items()) {
            String itemId=id(); db.update("INSERT INTO check_item(id,guide_version_id,sort_order,title,purpose,method,expected_observation,abnormal_signs,limitations) VALUES (?,?,?,?,?,?,?,?,?)",itemId,id,++order,item.title(),item.purpose(),String.join("\n",item.steps()),item.expectedObservation(),item.abnormalSigns(),item.limitations());
            if(item.images()!=null) {int imageOrder=0;for(Illustration image:item.images()) {
                db.update("INSERT INTO check_item_image(id,check_item_id,sort_order,url,alt,caption,kind,source_url,source_title) VALUES (?,?,?,?,?,?,?,?,?)",id(),itemId,++imageOrder,image.url(),image.alt(),image.caption(),image.kind(),image.sourceUrl(),image.sourceTitle());
            }}
            for(Source source:item.sources()) {
                String sourceId=id(); db.update("INSERT INTO source_ref(id,title,url,publisher) VALUES (?,?,?,?)",sourceId,source.title(),source.url(),source.publisher());
                db.update("INSERT INTO check_item_source(check_item_id,source_ref_id) VALUES (?,?)",itemId,sourceId);
            }
        }
        db.update("UPDATE guide_version SET title=?,tools=?,reviewed_on=?,content_note=?,edit_version=edit_version+1 WHERE id=?",in.title(),in.tools(),in.reviewedOn(),in.contentNote(),id);
        db.audit(actor,"GUIDE_SAVE",id,Map.of()); return version(id,false);
    }
    @Transactional public Map<String,Object> publish(String id,long expected,String actor) {
        var parent=db.one("SELECT guide_id FROM guide_version WHERE id=?",id);
        db.one("SELECT id FROM guide WHERE id=? FOR UPDATE",parent.get("guideId"));
        var v=db.one("SELECT * FROM guide_version WHERE id=? FOR UPDATE",id);
        check("DRAFT".equals(v.get("status"))&&((Number)v.get("editVersion")).longValue()==expected,409,"Draft changed or already published.");
        check(db.count("SELECT COUNT(*) FROM check_item WHERE guide_version_id=?",id)>0,400,"Add sourced inspection checks before publishing.");
        check(!v.get("tools").toString().isBlank()&&!v.get("contentNote").toString().isBlank(),400,"Complete the guide information.");
        db.update("UPDATE guide_version SET status='PUBLISHED',edit_version=edit_version+1 WHERE id=?",id);
        db.update("UPDATE guide SET availability='ACTIVE',withdrawal_reason=NULL WHERE id=?",v.get("guideId"));
        db.audit(actor,"GUIDE_PUBLISH",id,Map.of()); return version(id,false);
    }
    @Transactional public void withdraw(String id,String reason,String actor) {
        check(reason!=null&&!reason.isBlank()&&reason.length()<=600,400,"A withdrawal reason is required.");
        db.one("SELECT id FROM guide WHERE id=? FOR UPDATE",id);
        db.update("UPDATE guide SET availability='WITHDRAWN',withdrawal_reason=? WHERE id=?",reason,id);
        db.update("UPDATE guide_version SET status='WITHDRAWN' WHERE guide_id=? AND status='PUBLISHED'",id);
        db.audit(actor,"GUIDE_WITHDRAW",id,Map.of("reason",reason));
    }
    public Map<String,Object> version(String id,boolean publishedOnly) {
        var v=db.one("SELECT v.*,g.product_id,g.category_id,g.availability,g.withdrawal_reason FROM guide_version v JOIN guide g ON g.id=v.guide_id WHERE v.id=?",id);
        if(publishedOnly) check("PUBLISHED".equals(v.get("status"))&&"ACTIVE".equals(v.get("availability")),404,"Guide unavailable.");
        Object productId=v.get("productId");
        if(productId!=null) {
            var p=db.one("SELECT p.*,c.name AS category,c.slug AS category_slug FROM product p JOIN category c ON c.id=p.category_id WHERE p.id=?",productId);
            if(publishedOnly) check("ACTIVE".equals(p.get("status")),404,"Product unavailable.");
            v.put("product",p);v.put("scope","MODEL");
        } else { v.put("category",db.one("SELECT * FROM category WHERE id=?",v.get("categoryId")));v.put("scope","CATEGORY"); }
        v.put("versionId",v.get("id"));v.put("id",v.get("guideId"));v.put("version",v.get("versionNo"));
        var items=db.rows("SELECT * FROM check_item WHERE guide_version_id=? ORDER BY sort_order",id);
        for(var item:items) {
            item.put("order",item.get("sortOrder"));item.put("steps",item.remove("method").toString().lines().toList());
            item.put("images",db.rows("SELECT url,alt,caption,kind,source_url,source_title FROM check_item_image WHERE check_item_id=? ORDER BY sort_order",item.get("id")));
            item.put("sources",db.rows("SELECT s.title,s.url,s.publisher FROM source_ref s JOIN check_item_source x ON x.source_ref_id=s.id WHERE x.check_item_id=? ORDER BY s.id",item.get("id")));
        }
        v.put("items",items);return v;
    }
    public Map<String,Object> categoryGuide(String id) {
        var v=db.one("SELECT v.id FROM guide_version v JOIN guide g ON g.id=v.guide_id WHERE g.category_id=? AND g.availability='ACTIVE' AND v.status='PUBLISHED' ORDER BY v.version_no DESC LIMIT 1",id);
        return version(v.get("id").toString(),true);
    }
}
