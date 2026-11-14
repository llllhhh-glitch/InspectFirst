package com.inspectfirst.guide;

import com.inspectfirst.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.inspectfirst.shared.Store.*;

@Service
public class CommunityService {
    final Store db;
    public CommunityService(Store db) { this.db=db; }
    public record FeedbackInput(@NotBlank @Size(max=80) String requestKey,
        @NotNull @Pattern(regexp="INACCURATE|MISSING_PRODUCT|APP_PROBLEM") String type,
        String productId,String guideVersionId,String checkItemId,
        @NotBlank @Size(max=160) String subject,@NotBlank @Size(max=5000) String body) {}
    public record Reply(@NotBlank @Size(max=5000) String body,@Min(0) long expectedVersion) {}
    public record Status(@NotNull @Pattern(regexp="OPEN|IN_REVIEW|NEED_DETAILS|RESOLVED|CLOSED") String status,
        @NotBlank @Size(max=5000) String reply,@Min(0) long expectedVersion) {}
    @Transactional public Object save(String owner,String guideId) {
        var g=db.one("SELECT * FROM guide WHERE id=? FOR UPDATE",guideId);
        check("ACTIVE".equals(g.get("availability")),409,"This guide is unavailable.");
        var v=db.one("SELECT version_no FROM guide_version WHERE guide_id=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1",guideId);
        if(g.get("productId")!=null) check("ACTIVE".equals(db.one("SELECT status FROM product WHERE id=?",g.get("productId")).get("status")),409,"Product unavailable.");
        if(db.count("SELECT COUNT(*) FROM saved_guide WHERE owner_user_id=? AND guide_id=?",owner,guideId)==0)
            db.update("INSERT INTO saved_guide(owner_user_id,guide_id,seen_version) VALUES (?,?,?)",owner,guideId,v.get("versionNo"));
        return Map.of("saved",true,"guideId",guideId);
    }
    @Transactional public void unsave(String owner,String guideId) { db.update("DELETE FROM saved_guide WHERE owner_user_id=? AND guide_id=?",owner,guideId); }
    public Object saved(String owner,int page,int size) {
        int offset=offset(page,size);
        var rows=db.rows("SELECT s.*,g.availability,g.withdrawal_reason,g.product_id,g.category_id,COALESCE(p.name,c.name) AS name,p.status AS product_status,(SELECT MAX(v.version_no) FROM guide_version v WHERE v.guide_id=g.id AND v.status='PUBLISHED') AS latest_version FROM saved_guide s JOIN guide g ON g.id=s.guide_id LEFT JOIN product p ON p.id=g.product_id LEFT JOIN category c ON c.id=g.category_id WHERE s.owner_user_id=? ORDER BY s.created_at DESC,s.guide_id LIMIT ? OFFSET ?",owner,size,offset);
        for(var row:rows) {
            row.put("available","ACTIVE".equals(row.get("availability"))&&(row.get("productStatus")==null||"ACTIVE".equals(row.get("productStatus"))));
            row.put("updated",row.get("latestVersion") instanceof Number n&&n.intValue()>((Number)row.get("seenVersion")).intValue());
        }
        return db.page(rows,db.count("SELECT COUNT(*) FROM saved_guide WHERE owner_user_id=?",owner),page,size);
    }
    @Transactional public Object seen(String owner,String guideId) {
        var v=db.one("SELECT MAX(v.version_no) AS version_no FROM guide_version v JOIN guide g ON g.id=v.guide_id WHERE g.id=? AND g.availability='ACTIVE' AND v.status='PUBLISHED'",guideId);
        check(v.get("versionNo")!=null,404,"Guide unavailable.");
        check(db.update("UPDATE saved_guide SET seen_version=? WHERE owner_user_id=? AND guide_id=?",v.get("versionNo"),owner,guideId)==1,404,"Saved guide not found.");
        return Map.of("seen",true);
    }
    @Transactional public Object create(String owner,FeedbackInput input) {
        var old=db.rows("SELECT * FROM feedback WHERE owner_user_id=? AND request_key=?",owner,input.requestKey());
        String fingerprint=db.encode(input);
        if(!old.isEmpty()) { check(fingerprint.equals(old.get(0).get("requestPayload")),409,"Request key was used for different feedback.");return detail(owner,false,old.get(0).get("id").toString()); }
        if(input.productId()!=null) db.one("SELECT id FROM product WHERE id=?",input.productId());
        if(input.guideVersionId()!=null) {
            var v=db.one("SELECT g.product_id FROM guide_version v JOIN guide g ON g.id=v.guide_id WHERE v.id=? AND v.status IN ('PUBLISHED','WITHDRAWN')",input.guideVersionId());
            if(input.productId()!=null) check(input.productId().equals(v.get("productId")),400,"Guide does not match product.");
        }
        if(input.checkItemId()!=null) {
            var i=db.one("SELECT guide_version_id FROM check_item WHERE id=?",input.checkItemId());
            check(input.guideVersionId()!=null&&input.guideVersionId().equals(i.get("guideVersionId")),400,"Check does not match guide version.");
        }
        String id=id(); db.update("INSERT INTO feedback(id,owner_user_id,request_key,type,product_id,guide_version_id,check_item_id,subject,body,status,request_payload) VALUES (?,?,?,?,?,?,?,?,?,'OPEN',?)",id,owner,input.requestKey(),input.type(),input.productId(),input.guideVersionId(),input.checkItemId(),input.subject(),input.body(),fingerprint);
        return detail(owner,false,id);
    }
    public Map<String,Object> detail(String actor,boolean admin,String id) {
        var row=db.one("SELECT id,owner_user_id,type,product_id,guide_version_id,check_item_id,subject,body,status,lock_version,created_at,updated_at FROM feedback WHERE id=?"+(admin?"":" AND owner_user_id=?"),admin?new Object[]{id}:new Object[]{id,actor});
        row.put("replies",db.rows("SELECT id,actor_role,body,created_at FROM feedback_reply WHERE feedback_id=? ORDER BY created_at,id",id));return row;
    }
    public Object list(String owner,boolean admin,String status,String type,int page,int size) {
        int offset=offset(page,size); check(status.isEmpty()||Set.of("OPEN","IN_REVIEW","NEED_DETAILS","RESOLVED","CLOSED").contains(status),400,"Invalid feedback status.");
        check(type.isEmpty()||Set.of("INACCURATE","MISSING_PRODUCT","APP_PROBLEM").contains(type),400,"Invalid feedback type.");
        String where=" WHERE (?='' OR status=?) AND (?='' OR type=?)"+(admin?"":" AND owner_user_id=?");
        List<Object> args=new ArrayList<>(List.of(status,status,type,type));if(!admin) args.add(owner);
        long count=db.count("SELECT COUNT(*) FROM feedback"+where,args.toArray());args.add(size);args.add(offset);
        return db.page(db.rows("SELECT id,owner_user_id,type,product_id,subject,status,lock_version,created_at,updated_at FROM feedback"+where+" ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",args.toArray()),count,page,size);
    }
    @Transactional public Object reply(String owner,String id,Reply input) {
        var row=locked(id,owner,false,input.expectedVersion());
        check(!"CLOSED".equals(row.get("status")),409,"This feedback is closed. Create a new feedback request.");
        addReply(id,owner,"USER",input.body());
        String status=Set.of("RESOLVED","NEED_DETAILS").contains(row.get("status"))?"IN_REVIEW":row.get("status").toString();
        db.update("UPDATE feedback SET status=?,lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,id);
        return detail(owner,false,id);
    }
    @Transactional public Object status(String admin,String id,Status input) {
        var row=locked(id,admin,true,input.expectedVersion());
        String old=row.get("status").toString();
        Map<String,Set<String>> allowed=Map.of("OPEN",Set.of("IN_REVIEW","NEED_DETAILS","CLOSED"),"IN_REVIEW",Set.of("NEED_DETAILS","RESOLVED","CLOSED"),"NEED_DETAILS",Set.of("IN_REVIEW","CLOSED"),"RESOLVED",Set.of("IN_REVIEW","CLOSED"),"CLOSED",Set.of());
        check(allowed.get(old).contains(input.status()),409,"Invalid feedback state transition.");
        addReply(id,admin,"ADMIN",input.reply());
        db.update("UPDATE feedback SET status=?,lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.status(),id);
        db.audit(admin,"FEEDBACK_"+input.status(),id,Map.of());return detail(admin,true,id);
    }
    private Map<String,Object> locked(String id,String actor,boolean admin,long expected) {
        var row=db.one("SELECT * FROM feedback WHERE id=?"+(admin?"":" AND owner_user_id=?")+" FOR UPDATE",admin?new Object[]{id}:new Object[]{id,actor});
        check(((Number)row.get("lockVersion")).longValue()==expected,409,"Feedback changed. Refresh before updating.");return row;
    }
    private void addReply(String id,String actor,String role,String body) { db.update("INSERT INTO feedback_reply(id,feedback_id,actor_id,actor_role,body) VALUES (?,?,?,?,?)",id(),id,actor,role,body); }
}
