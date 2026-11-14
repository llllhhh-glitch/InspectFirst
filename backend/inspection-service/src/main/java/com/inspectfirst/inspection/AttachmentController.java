package com.inspectfirst.inspection;
import com.inspectfirst.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;

@RestController
public class AttachmentController {
    private final AttachmentService files;private final SessionAccess auth;
    public AttachmentController(AttachmentService files,SessionAccess auth) { this.files=files;this.auth=auth; }
    @PostMapping("/api/inspections/{id}/items/{item}/attachments") Object upload(@PathVariable String id,@PathVariable String item,@RequestParam long expectedVersion,@RequestParam MultipartFile file,HttpServletRequest r) throws IOException { return files.upload(auth.require(r,false).id(),id,item,expectedVersion,file); }
    @GetMapping("/api/attachments/{id}") ResponseEntity<byte[]> download(@PathVariable String id,HttpServletRequest r) throws IOException {
        var actor=auth.require(r,false);var file=files.authorize(actor.id(),actor.admin(),id);
        return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=\"inspection-photo\"")
            .contentType(MediaType.parseMediaType(file.get("contentType").toString())).body(files.download(actor.id(),actor.admin(),id));
    }
    @DeleteMapping("/api/attachments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable String id,@RequestParam long expectedVersion,HttpServletRequest r) { files.delete(auth.require(r,false).id(),id,expectedVersion); }
}
