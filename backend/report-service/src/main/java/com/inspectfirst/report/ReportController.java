package com.inspectfirst.report;

import com.inspectfirst.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import static com.inspectfirst.shared.Store.*;

@RestController
public class ReportController {
    private final Store db;private final SessionAccess auth;private final RestClient inspection;
    public ReportController(Store db,SessionAccess auth,@Value("${services.inspection:http://127.0.0.1:8088}") String url) {
        this.db=db;this.auth=auth;var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(5));
        inspection=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @GetMapping("/api/reports/health") Object health() { return Map.of("status","UP","service","report-service"); }
    @GetMapping("/api/ops/reports") Object operations(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) {
        auth.require(r,true);int offset=offset(page,size);return db.page(db.rows("SELECT id,inspection_id,revision_id,revision_no,owner_user_id,status,attempt_count,error_code,created_at,finished_at FROM report_job ORDER BY created_at DESC,id LIMIT ? OFFSET ?",size,offset),db.count("SELECT COUNT(*) FROM report_job"),page,size);
    }
    @GetMapping("/api/reports") Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) {
        var actor=auth.require(r,false);int offset=offset(page,size);
        return db.page(db.rows("SELECT id,inspection_id,revision_id,revision_no,status,snapshot_hash,attempt_count,error_code,created_at,finished_at FROM report_job WHERE owner_user_id=? AND status<>'CANCELLED' ORDER BY created_at DESC,id LIMIT ? OFFSET ?",actor.id(),size,offset),db.count("SELECT COUNT(*) FROM report_job WHERE owner_user_id=? AND status<>'CANCELLED'",actor.id()),page,size);
    }
    Map<String,Object> authorized(String id,HttpServletRequest r) {
        var actor=auth.require(r,false);var job=db.one("SELECT * FROM report_job WHERE id=?"+(actor.admin()?"":" AND owner_user_id=?"),actor.admin()?new Object[]{id}:new Object[]{id,actor.id()});
        check(!"CANCELLED".equals(job.get("status")),404,"Report unavailable.");
        try {
            inspection.get().uri(actor.admin()?"/api/ops/inspections/{id}":"/api/inspections/{id}",job.get("inspectionId"))
                .header("Cookie",Objects.toString(r.getHeader("Cookie"),"")).retrieve().toBodilessEntity();
        } catch(RestClientResponseException e) { throw new ResponseStatusException(e.getStatusCode().value()==404?HttpStatus.NOT_FOUND:HttpStatus.SERVICE_UNAVAILABLE,"Inspection is no longer accessible."); }
        catch(ResourceAccessException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cannot verify inspection access. Retry later."); }
        return job;
    }
    @GetMapping("/api/reports/{id}") Object detail(@PathVariable String id,HttpServletRequest r) {
        var row=authorized(id,r);row.remove("snapshotJson");row.remove("reportHtml");return row;
    }
    @GetMapping("/api/reports/{id}/download") ResponseEntity<String> download(@PathVariable String id,HttpServletRequest r) {
        var row=authorized(id,r);check("READY".equals(row.get("status")),409,"Report is not ready.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            .header("Content-Disposition","attachment; filename=\"InspectFirst-report.html\"").body(row.get("reportHtml").toString());
    }
    @GetMapping("/api/reports/{id}/snapshot") Object snapshot(@PathVariable String id,HttpServletRequest r) { var row=authorized(id,r);return db.decode(row.get("snapshotJson")); }
    @GetMapping("/api/reports/{id}/download.pdf") ResponseEntity<byte[]> pdf(@PathVariable String id,HttpServletRequest r) throws java.io.IOException {
        var row=authorized(id,r);check("READY".equals(row.get("status")),409,"Report is not ready.");
        check(ReportEvents.hash(row.get("snapshotJson").toString()).equals(row.get("snapshotHash")),409,"Report integrity check failed.");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Cache-Control","no-store")
            .header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=\"InspectFirst-report.pdf\"")
            .body(PdfExport.render(db.decode(row.get("snapshotJson")),((Number)row.get("revisionNo")).intValue(),row.get("snapshotHash").toString()));
    }
    @PostMapping("/api/reports/{id}/retry") Object retry(@PathVariable String id,HttpServletRequest r) {
        var row=authorized(id,r);check("FAILED".equals(row.get("status")),409,"Only failed reports can be retried.");
        check(db.update("UPDATE report_job SET status='QUEUED',error_code=NULL WHERE id=? AND status='FAILED'",id)==1,409,"Report changed.");return Map.of("status","QUEUED");
    }
}
