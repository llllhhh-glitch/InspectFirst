package com.inspectfirst.inspection;

import com.inspectfirst.shared.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static com.inspectfirst.shared.Store.*;

@Service
public class AttachmentService {
    private final InspectionService inspections;private final Store db;private final Path directory;
    public AttachmentService(InspectionService inspections,Store db,@Value("${inspectfirst.attachments:../../.local/attachments}") String directory) {
        this.inspections=inspections;this.db=db;this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    @Transactional public Object upload(String owner,String inspection,String item,long expected,MultipartFile file) throws IOException {
        var row=inspections.row(owner,false,inspection,true,false);
        check("IN_PROGRESS".equals(row.get("status"))&&((Number)row.get("lockVersion")).longValue()==expected,409,"Inspection changed or is completed.");
        db.one("SELECT id FROM inspection_item WHERE id=? AND inspection_id=?",item,inspection);
        check(db.count("SELECT COUNT(*) FROM inspection_attachment WHERE item_id=?",item)<5,409,"At most five photos per check.");
        byte[] data=file.getBytes();check(data.length>8&&data.length<=5*1024*1024,400,"Image must be at most 5 MB.");
        boolean png=data[0]==(byte)137&&data[1]==80&&data[2]==78&&data[3]==71&&data[4]==13&&data[5]==10&&data[6]==26&&data[7]==10;
        boolean jpeg=data[0]==(byte)255&&data[1]==(byte)216&&data[2]==(byte)255;
        check(png||jpeg,400,"Only PNG and JPEG photos are accepted.");
        String id=id(),type=png?"image/png":"image/jpeg";
        Files.createDirectories(directory);Path path=directory.resolve(id+".bin");Files.write(path,data,StandardOpenOption.CREATE_NEW);
        // If the surrounding transaction fails, remove the newly written object as compensation.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) try { Files.deleteIfExists(path); } catch(IOException ignored) {} }
        });
        db.update("INSERT INTO inspection_attachment(id,inspection_id,item_id,content_type,size_bytes,sha256) VALUES (?,?,?,?,?,?)",id,inspection,item,type,data.length,sha(data));
        db.update("UPDATE inspection SET lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",inspection);
        return Map.of("id",id,"contentType",type,"sizeBytes",data.length,"lockVersion",expected+1);
    }
    static String sha(byte[] data) { try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)); } catch(Exception e) { throw new IllegalStateException(e); } }
    Map<String,Object> authorize(String actor,boolean admin,String id) {
        var file=db.one("SELECT * FROM inspection_attachment WHERE id=?",id);
        inspections.row(actor,admin,file.get("inspectionId").toString(),false,false);return file;
    }
    byte[] download(String actor,boolean admin,String id) throws IOException {
        var file=authorize(actor,admin,id);byte[] bytes=Files.readAllBytes(directory.resolve(file.get("id")+".bin"));
        check(sha(bytes).equals(file.get("sha256")),409,"Stored image failed integrity validation.");
        if(admin) db.audit(actor,"ATTACHMENT_READ",file.get("inspectionId").toString(),Map.of("attachmentId",id));
        return bytes;
    }
    @Transactional public void delete(String owner,String id,long expected) {
        var file=authorize(owner,false,id);String inspection=file.get("inspectionId").toString();
        var row=inspections.row(owner,false,inspection,true,false);
        check("IN_PROGRESS".equals(row.get("status"))&&((Number)row.get("lockVersion")).longValue()==expected,409,"Inspection changed or is completed.");
        check(db.count("SELECT COUNT(*) FROM inspection_revision WHERE inspection_id=? AND snapshot_json LIKE ?",inspection,"%"+file.get("id")+"%") == 0,
            409,"This photo belongs to a completed revision and is retained with that history. Delete the inspection to revoke access.");
        db.update("DELETE FROM inspection_attachment WHERE id=?",id);db.update("UPDATE inspection SET lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",inspection);
        // Completed revisions retain attachment metadata; no absolute file paths are exposed.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { try { Files.deleteIfExists(directory.resolve(id+".bin")); } catch(IOException ignored) {} }
        });
    }
}
