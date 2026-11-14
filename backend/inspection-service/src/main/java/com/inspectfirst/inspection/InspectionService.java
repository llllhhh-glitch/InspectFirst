package com.inspectfirst.inspection;

import com.inspectfirst.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.inspectfirst.shared.Store.*;

@Service
public class InspectionService {
    final Store db; private final GuideClient guides;
    public InspectionService(Store db,GuideClient guides) { this.db=db;this.guides=guides; }
    public record Create(String productId,String categoryId,@NotBlank String guideVersionId,@NotBlank @Size(max=80) String requestKey,
        @NotNull @Size(max=3000) String userRequirement,@NotNull @Size(max=3000) String sellerClaim) {}
    public record Result(@NotBlank String itemId,@NotNull @Pattern(regexp="UNCHECKED|NO_ISSUE_OBSERVED|ISSUE_OBSERVED|UNABLE_TO_ASSESS") String status,
        @NotNull @Size(max=3000) String note) {}
    public record Save(@Min(0) long expectedVersion,@NotNull @Size(max=3000) String userRequirement,@NotNull @Size(max=3000) String sellerClaim,
        @NotNull @Size(max=40) List<@Valid Result> results) {}
    public record Complete(@Min(0) long expectedVersion,@NotBlank @Size(max=80) String requestKey) {}
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional public Map<String,Object> create(String owner,Create input) {
        check((input.productId()!=null) != (input.categoryId()!=null),400,"Choose one product or category.");
        var old=db.rows("SELECT id,request_payload,status FROM inspection WHERE owner_user_id=? AND request_key=?",owner,input.requestKey());
        if(!old.isEmpty()) {
            check(old.get(0).get("requestPayload").equals(db.encode(input)),409,"Request key already used for different inspection details.");
            check(!"DELETED".equals(old.get(0).get("status")),409,"This request created a now-deleted inspection. Use a new key.");
            return detail(owner,false,old.get(0).get("id").toString());
        }
        var guide=guides.snapshot(input.productId(),input.categoryId());
        check(guide!=null&&guide.path("versionId").asText().equals(input.guideVersionId()),409,"The guide changed. Read the current version first.");
        check(guide.path("items").isArray()&&guide.path("items").size()>0&&guide.path("items").size()<=40,409,"Guide has no usable checks.");
        String id=id();db.update("INSERT INTO inspection(id,owner_user_id,product_id,category_id,guide_version_id,guide_snapshot,user_requirement,seller_claim,status,request_key,request_payload) VALUES (?,?,?,?,?,?,?,?,'IN_PROGRESS',?,?)",id,owner,input.productId(),input.categoryId(),input.guideVersionId(),db.encode(guide),input.userRequirement(),input.sellerClaim(),input.requestKey(),db.encode(input));
        for(var item:guide.path("items")) db.update("INSERT INTO inspection_item(id,inspection_id,source_item_id,sort_order,content_snapshot,result_status,note) VALUES (?,?,?,?,?,'UNCHECKED','')",id(),id,item.path("id").asText(),item.path("order").asInt(),db.encode(item));
        return detail(owner,false,id);
    }
    Map<String,Object> row(String actor,boolean admin,String id,boolean lock,boolean deleted) {
        return db.one("SELECT * FROM inspection WHERE id=?"+(admin?"":" AND owner_user_id=?")+(deleted?"":" AND status<>'DELETED'")+(lock?" FOR UPDATE":""),admin?new Object[]{id}:new Object[]{id,actor});
    }
    public Map<String,Object> detail(String actor,boolean admin,String id) {
        var row=row(actor,admin,id,false,false);row.remove("requestPayload");
        row.put("guideSnapshot",db.decode(row.get("guideSnapshot")));
        var items=db.rows("SELECT * FROM inspection_item WHERE inspection_id=? ORDER BY sort_order",id);
        for(var item:items) { item.put("contentSnapshot",db.decode(item.get("contentSnapshot"))); item.put("attachments",db.rows("SELECT id,content_type,size_bytes,sha256,created_at FROM inspection_attachment WHERE item_id=? ORDER BY created_at,id",item.get("id"))); }
        row.put("items",items);row.put("summary",summary(items));return row;
    }
    static Map<String,Object> summary(List<Map<String,Object>> items) {
        Map<String,Object> counts=new LinkedHashMap<>();
        for(String status:List.of("UNCHECKED","NO_ISSUE_OBSERVED","ISSUE_OBSERVED","UNABLE_TO_ASSESS")) counts.put(status,items.stream().filter(i->status.equals(i.get("resultStatus"))).count());
        counts.put("total",items.size());counts.put("note","Observations only. Unchecked and inconclusive items are not passes. No authenticity or quality guarantee.");return counts;
    }
    @Transactional public Map<String,Object> save(String owner,String id,Save input) {
        var row=editable(owner,id,input.expectedVersion());
        Set<String> seen=new HashSet<>();
        for(Result result:input.results()) {
            check(seen.add(result.itemId()),400,"Duplicate check result.");
            check(db.update("UPDATE inspection_item SET result_status=?,note=? WHERE id=? AND inspection_id=?",result.status(),result.note(),result.itemId(),id)==1,400,"Check does not belong to this inspection.");
        }
        db.update("UPDATE inspection SET user_requirement=?,seller_claim=?,lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.userRequirement(),input.sellerClaim(),id);
        return detail(owner,false,id);
    }
    private Map<String,Object> editable(String owner,String id,long expected) {
        var row=row(owner,false,id,true,false);
        check("IN_PROGRESS".equals(row.get("status")),409,"Reopen a completed inspection before editing.");
        check(((Number)row.get("lockVersion")).longValue()==expected,409,"Inspection changed. Refresh before saving.");return row;
    }
    @Transactional public Object complete(String owner,String id,Complete input) {
        var row=row(owner,false,id,true,false);
        var old=db.rows("SELECT id FROM inspection_revision WHERE inspection_id=? AND request_key=?",id,input.requestKey());
        if(!old.isEmpty()) return revision(owner,false,id,old.get(0).get("id").toString());
        editable(owner,id,input.expectedVersion());
        int number=((Number)row.get("latestRevisionNo")).intValue()+1;String revision=id();
        db.update("UPDATE inspection SET status='COMPLETED',latest_revision_no=?,lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",number,id);
        var snapshot=detail(owner,false,id);String encoded=db.encode(snapshot),sha=hash(encoded);
        db.update("INSERT INTO inspection_revision(id,inspection_id,revision_no,snapshot_json,snapshot_hash,request_key) VALUES (?,?,?,?,?,?)",revision,id,number,encoded,sha,input.requestKey());
        String event=id();var payload=new LinkedHashMap<String,Object>();
        payload.put("eventId",event);payload.put("type","INSPECTION_COMPLETED");payload.put("inspectionId",id);payload.put("revisionId",revision);payload.put("revisionNo",number);payload.put("ownerUserId",owner);payload.put("snapshot",snapshot);payload.put("snapshotHash",sha);
        String eventPayload=db.encode(payload);
        check(eventPayload.getBytes(StandardCharsets.UTF_8).length<=7*1024*1024,400,"Inspection report payload exceeds the supported size.");
        db.update("INSERT INTO outbox_event(id,aggregate_id,event_type,payload) VALUES (?,?,?,?)",event,id,"INSPECTION_COMPLETED",eventPayload);
        return revision(owner,false,id,revision);
    }
    @Transactional public Object reopen(String owner,String id,long expected) {
        var row=row(owner,false,id,true,false);check("COMPLETED".equals(row.get("status")),409,"Only completed inspections can be reopened.");
        check(((Number)row.get("lockVersion")).longValue()==expected,409,"Inspection changed.");
        db.update("UPDATE inspection SET status='IN_PROGRESS',lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);return detail(owner,false,id);
    }
    @Transactional public void delete(String owner,String id,long expected) {
        var row=row(owner,false,id,true,true);if("DELETED".equals(row.get("status"))) return;
        check(((Number)row.get("lockVersion")).longValue()==expected,409,"Inspection changed.");
        db.update("UPDATE inspection SET status='DELETED',lock_version=lock_version+1,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        String event=id();db.update("INSERT INTO outbox_event(id,aggregate_id,event_type,payload) VALUES (?,?,?,?)",event,id,"INSPECTION_DELETED",db.encode(Map.of("eventId",event,"type","INSPECTION_DELETED","inspectionId",id,"ownerUserId",owner)));
    }
    public Object list(String actor,boolean admin,String owner,String status,int page,int size) {
        int offset=offset(page,size);check(status.isEmpty()||Set.of("IN_PROGRESS","COMPLETED","DELETED").contains(status),400,"Invalid inspection status.");
        if(!admin) { owner=actor;check(!"DELETED".equals(status),400,"Deleted inspections are unavailable."); }
        String where=" WHERE (?='' OR owner_user_id=?) AND (?='' OR status=?)"+(admin?"":" AND status<>'DELETED'");
        long total=db.count("SELECT COUNT(*) FROM inspection"+where,owner,owner,status,status);
        var rows=db.rows("SELECT id,owner_user_id,product_id,category_id,guide_version_id,status,lock_version,latest_revision_no,created_at,updated_at,deleted_at FROM inspection"+where+" ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",owner,owner,status,status,size,offset);
        return db.page(rows,total,page,size);
    }
    public Object revisions(String actor,boolean admin,String id) {
        row(actor,admin,id,false,false);return db.rows("SELECT id,revision_no,snapshot_hash,created_at FROM inspection_revision WHERE inspection_id=? ORDER BY revision_no DESC",id);
    }
    public Object revision(String actor,boolean admin,String id,String revision) {
        row(actor,admin,id,false,false);var v=db.one("SELECT id,revision_no,snapshot_json,snapshot_hash,created_at FROM inspection_revision WHERE id=? AND inspection_id=?",revision,id);
        v.put("snapshot",db.decode(v.remove("snapshotJson")));return v;
    }
}
