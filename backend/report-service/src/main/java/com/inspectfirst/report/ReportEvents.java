package com.inspectfirst.report;

import com.inspectfirst.shared.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.inspectfirst.shared.Store.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ReportEvents {
    private final Store db;
    public ReportEvents(Store db) { this.db=db; }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional public void accept(String message) {
        check(message.getBytes(StandardCharsets.UTF_8).length<=7*1024*1024,400,"Event too large.");var event=db.decode(message);
        String eventId=event.path("eventId").asText(),id=event.path("inspectionId").asText(),type=event.path("type").asText();
        java.util.UUID.fromString(eventId);java.util.UUID.fromString(id);
        check(java.util.Set.of("INSPECTION_COMPLETED","INSPECTION_DELETED").contains(type),400,"Unknown event schema.");
        if(db.update("INSERT IGNORE INTO inbox_event(event_id) VALUES (?)",eventId)==0) return;
        db.update("INSERT IGNORE INTO report_aggregate(inspection_id) VALUES (?)",id);
        var aggregate=db.one("SELECT * FROM report_aggregate WHERE inspection_id=? FOR UPDATE",id);
        if(type.equals("INSPECTION_DELETED")) {
            db.update("UPDATE report_aggregate SET deleted_flag=TRUE WHERE inspection_id=?",id);
            db.update("UPDATE report_job SET status='CANCELLED',snapshot_json=NULL,report_html=NULL,finished_at=CURRENT_TIMESTAMP WHERE inspection_id=?",id);return;
        }
        if(Boolean.TRUE.equals(aggregate.get("deletedFlag"))) return;
        String snapshot=db.encode(event.path("snapshot"));
        check(hash(snapshot).equals(event.path("snapshotHash").asText()),400,"Snapshot integrity mismatch.");
        db.update("INSERT IGNORE INTO report_job(id,inspection_id,revision_id,revision_no,owner_user_id,status,snapshot_json,snapshot_hash) VALUES (?,?,?,?,?,'QUEUED',?,?)",
            id(),id,event.path("revisionId").asText(),event.path("revisionNo").asInt(),event.path("ownerUserId").asText(),snapshot,event.path("snapshotHash").asText());
    }
}
