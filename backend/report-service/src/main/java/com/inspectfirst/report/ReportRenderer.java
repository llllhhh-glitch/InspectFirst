package com.inspectfirst.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.inspectfirst.shared.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportRenderer {
    private final Store db;
    public ReportRenderer(Store db) { this.db=db; }
    @Transactional public void next() {
        var jobs=db.rows("SELECT * FROM report_job WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE");
        if(jobs.isEmpty()) return;var job=jobs.get(0);
        try {
            String encoded=job.get("snapshotJson").toString();
            if(!ReportEvents.hash(encoded).equals(job.get("snapshotHash"))) throw new IllegalStateException("Snapshot integrity");
            String html=render(db.decode(encoded),((Number)job.get("revisionNo")).intValue(),job.get("snapshotHash").toString());
            db.update("UPDATE report_job SET status='READY',report_html=?,attempt_count=attempt_count+1,finished_at=CURRENT_TIMESTAMP,error_code=NULL WHERE id=?",html,job.get("id"));
        } catch(Exception e) {
            db.update("UPDATE report_job SET status='FAILED',attempt_count=attempt_count+1,error_code='RENDER_FAILED' WHERE id=?",job.get("id"));
        }
    }
    static String escape(Object text) { return String.valueOf(text).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    public static String render(JsonNode snapshot,int revision,String hash) {
        var guide=snapshot.path("guideSnapshot");String name=guide.path("product").path("name").asText(guide.path("category").path("name").asText("Inspection"));
        StringBuilder html=new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>InspectFirst inspection report</title><style>body{font:15px/1.6 sans-serif;max-width:850px;margin:35px auto;padding:20px;color:#24342b}section{break-inside:avoid;border-top:1px solid #ddd;padding:18px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere}small{color:#65716a}h1{line-height:1.3}@media print{body{margin:0}}</style><body><h1>InspectFirst · Inspection report</h1>");
        html.append("<h2>").append(escape(name)).append("</h2><p>Completion revision ").append(revision).append(" · Guide version ").append(guide.path("version").asInt()).append("</p>");
        html.append("<p><strong>Self-check observations only. Not an authenticity certificate or quality guarantee. Unchecked and inconclusive checks are not passes.</strong></p>");
        html.append("<h3>Purchase requirements</h3><pre>").append(escape(snapshot.path("userRequirement").asText())).append("</pre><h3>Seller claims</h3><pre>").append(escape(snapshot.path("sellerClaim").asText())).append("</pre>");
        html.append("<h3>Summary</h3><ul>");
        for(String status:java.util.List.of("NO_ISSUE_OBSERVED","ISSUE_OBSERVED","UNCHECKED","UNABLE_TO_ASSESS")) html.append("<li>").append(status).append(": ").append(snapshot.path("summary").path(status).asLong()).append("</li>");
        html.append("</ul>");
        for(var item:snapshot.path("items")) {
            var content=item.path("contentSnapshot");html.append("<section><h3>").append(escape(content.path("title").asText())).append("</h3><p><strong>").append(escape(item.path("resultStatus").asText())).append("</strong></p><pre>").append(escape(item.path("note").asText())).append("</pre><ol>");
            for(var step:content.path("steps")) html.append("<li>").append(escape(step.asText())).append("</li>");
            html.append("</ol><p>Expected: ").append(escape(content.path("expectedObservation").asText())).append("</p><p>Possible problems: ").append(escape(content.path("abnormalSigns").asText())).append("</p><p>Limits: ").append(escape(content.path("limitations").asText())).append("</p><ul>");
            for(var source:content.path("sources")) html.append("<li>").append(escape(source.path("title").asText())).append(" — ").append(escape(source.path("url").asText())).append("</li>");
            html.append("</ul></section>");
        }
        return html.append("<small>Snapshot SHA-256: ").append(escape(hash)).append(". Integrity metadata is not a certification.</small></body></html>").toString();
    }
}
