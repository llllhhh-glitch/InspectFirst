package com.inspectfirst.inspection;

import com.inspectfirst.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
public class InspectionController {
    private final InspectionService service;private final SessionAccess auth;private final Store db;
    public InspectionController(InspectionService service,SessionAccess auth,Store db) { this.service=service;this.auth=auth;this.db=db; }
    record Version(@Min(0) long expectedVersion) {}
    @GetMapping("/api/ops/inspections/stats") Object stats(HttpServletRequest r) { auth.require(r,true);return db.rows("SELECT status,COUNT(*) AS total FROM inspection GROUP BY status ORDER BY status"); }
    @GetMapping("/api/ops/inspections/audit") Object audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { auth.require(r,true);int offset=Store.offset(page,size);return db.page(db.rows("SELECT * FROM audit_log ORDER BY created_at DESC,id LIMIT ? OFFSET ?",size,offset),db.count("SELECT COUNT(*) FROM audit_log"),page,size); }
    @GetMapping("/api/inspections/health") Object health() { return Map.of("status","UP","service","inspection-service"); }
    @PostMapping("/api/inspections") Object create(@Valid @RequestBody InspectionService.Create input,HttpServletRequest r) { return service.create(auth.require(r,false).id(),input); }
    @GetMapping("/api/inspections") Object list(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { return service.list(auth.require(r,false).id(),false,"",status,page,size); }
    @GetMapping("/api/inspections/{id}") Object detail(@PathVariable String id,HttpServletRequest r) { return service.detail(auth.require(r,false).id(),false,id); }
    @PutMapping("/api/inspections/{id}") Object save(@PathVariable String id,@Valid @RequestBody InspectionService.Save input,HttpServletRequest r) { return service.save(auth.require(r,false).id(),id,input); }
    @PostMapping("/api/inspections/{id}/complete") Object complete(@PathVariable String id,@Valid @RequestBody InspectionService.Complete input,HttpServletRequest r) { return service.complete(auth.require(r,false).id(),id,input); }
    @PostMapping("/api/inspections/{id}/reopen") Object reopen(@PathVariable String id,@Valid @RequestBody Version input,HttpServletRequest r) { return service.reopen(auth.require(r,false).id(),id,input.expectedVersion()); }
    @DeleteMapping("/api/inspections/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable String id,@RequestParam long expectedVersion,HttpServletRequest r) { service.delete(auth.require(r,false).id(),id,expectedVersion); }
    @GetMapping("/api/inspections/{id}/revisions") Object revisions(@PathVariable String id,HttpServletRequest r) { return service.revisions(auth.require(r,false).id(),false,id); }
    @GetMapping("/api/inspections/{id}/revisions/{revision}") Object revision(@PathVariable String id,@PathVariable String revision,HttpServletRequest r) { return service.revision(auth.require(r,false).id(),false,id,revision); }
    @GetMapping("/api/ops/inspections") Object staffList(@RequestParam(defaultValue="") String ownerUserId,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { return service.list(auth.require(r,true).id(),true,ownerUserId,status,page,size); }
    @GetMapping("/api/ops/inspections/{id}") Object staffDetail(@PathVariable String id,HttpServletRequest r) {
        var actor=auth.require(r,true);var result=service.detail(actor.id(),true,id);db.audit(actor.id(),"INSPECTION_READ",id,Map.of());return result;
    }
    @GetMapping("/api/ops/inspections/{id}/revisions") Object staffRevisions(@PathVariable String id,HttpServletRequest r) { return service.revisions(auth.require(r,true).id(),true,id); }
    @GetMapping("/api/ops/inspections/{id}/revisions/{revision}") Object staffRevision(@PathVariable String id,@PathVariable String revision,HttpServletRequest r) { var actor=auth.require(r,true);var result=service.revision(actor.id(),true,id,revision);db.audit(actor.id(),"REVISION_READ",id,Map.of("revisionId",revision));return result; }
}
