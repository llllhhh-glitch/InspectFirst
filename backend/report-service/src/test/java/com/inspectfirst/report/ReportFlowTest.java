package com.inspectfirst.report;

import com.inspectfirst.shared.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportFlowTest {
    @Autowired Store db;@Autowired ReportEvents events;@Autowired ReportRenderer renderer;
    Map<String,Object> complete(String id) {
        var snapshot=Map.of("userRequirement","<script>alert(1)</script>","guideSnapshot",Map.of("product",Map.of("name","Camera")),"items",List.of(),"summary",Map.of());
        return Map.of("eventId",Store.id(),"type","INSPECTION_COMPLETED","inspectionId",id,"revisionId",Store.id(),"revisionNo",1,"ownerUserId","owner","snapshot",snapshot,"snapshotHash",ReportEvents.hash(db.encode(snapshot)));
    }
    @Test void duplicateEventCreatesOneJobAndRenderingEscapesUserText() {
        String id=Store.id();var event=complete(id);events.accept(db.encode(event));events.accept(db.encode(event));
        assertEquals(1,db.count("SELECT COUNT(*) FROM report_job WHERE inspection_id=?",id));renderer.next();
        var job=db.one("SELECT * FROM report_job WHERE inspection_id=?",id);assertEquals("READY",job.get("status"));
        assertFalse(job.get("reportHtml").toString().contains("<script>"));assertTrue(job.get("reportHtml").toString().contains("&lt;script&gt;"));
    }
    @Test void deletionBeforeCompletionCannotResurrectReport() {
        String id=Store.id();events.accept(db.encode(Map.of("eventId",Store.id(),"type","INSPECTION_DELETED","inspectionId",id)));
        events.accept(db.encode(complete(id)));assertEquals(0,db.count("SELECT COUNT(*) FROM report_job WHERE inspection_id=?",id));
    }
    @Test void deletionCancelsExistingJobAndErasesReportSnapshot() {
        String id=Store.id();events.accept(db.encode(complete(id)));events.accept(db.encode(Map.of("eventId",Store.id(),"type","INSPECTION_DELETED","inspectionId",id)));
        var row=db.one("SELECT * FROM report_job WHERE inspection_id=?",id);assertEquals("CANCELLED",row.get("status"));assertNull(row.get("snapshotJson"));
    }
    @Test void pdfExportsReadableEnglishAndChineseWithMultiplePages() throws Exception {
        var snapshot=db.decode(db.encode(Map.of("userRequirement","Test requirement 中文验货要求".repeat(150),"sellerClaim","Seller description","guideSnapshot",Map.of("product",Map.of("name","Test Camera")),"items",List.of(),"summary",Map.of())));
        byte[] pdf=PdfExport.render(snapshot,1,"sample-hash");
        java.nio.file.Files.write(java.nio.file.Path.of("target/qa-report.pdf"),pdf);
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages()>1);
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/qa-report-text.txt"),text);
            assertTrue(text.contains("Test Camera"),"Product title missing");
            assertTrue(java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).contains("中文验货要求"),"Chinese text missing");
            assertTrue(text.contains("UNCHECKED"),"Status missing");
        }
    }
}
