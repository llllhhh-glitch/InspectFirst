package com.inspectfirst.inspection;

import com.inspectfirst.shared.Store;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties="inspectfirst.attachments=target/test-attachments")
class InspectionFlowTest {
    @Autowired InspectionService service;@Autowired Store db;
    @Autowired AttachmentService attachments;
    @MockitoBean GuideClient guides;
    @MockitoBean com.inspectfirst.shared.SessionAccess auth;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Test void administratorRecordDetailAndAuditWorkWithTransactionalServiceProxy() throws Exception {
        when(auth.require(any(),eq(true))).thenReturn(new com.inspectfirst.shared.SessionAccess.Actor("admin","admin@example.test","Admin","ADMIN"));
        String id=service.create("admin-audit-owner",input()).get("id").toString();
        for(String path:List.of("/api/ops/inspections/"+id,"/api/ops/inspections/stats","/api/ops/inspections/audit"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_log WHERE target_id=?",id));
    }
    @BeforeEach void guide() {
        when(guides.snapshot("product",null)).thenReturn(db.decode("{\"id\":\"guide\",\"versionId\":\"version\",\"version\":1,\"product\":{\"name\":\"Camera\"},\"items\":[{\"id\":\"source-a\",\"order\":1,\"title\":\"Check\"},{\"id\":\"source-b\",\"order\":2,\"title\":\"Other\"}]}"));
    }
    InspectionService.Create input() { return new InspectionService.Create("product",null,"version",UUID.randomUUID().toString(),"Works","No defects"); }
    @Test void ownershipSaveConflictCompletionRevisionReopenAndDeletion() {
        var row=service.create("owner-a",input());String id=row.get("id").toString();
        assertThrows(ResponseStatusException.class,()->service.detail("owner-b",false,id));
        var items=(List<Map<String,Object>>)row.get("items");String item=items.get(0).get("id").toString();
        var save=new InspectionService.Save(0,"Works","No defects",List.of(new InspectionService.Result(item,"ISSUE_OBSERVED","Scratch")));
        service.save("owner-a",id,save);
        assertThrows(ResponseStatusException.class,()->service.save("owner-a",id,save));
        var complete=new InspectionService.Complete(1,UUID.randomUUID().toString());
        var rev=(Map<String,Object>)service.complete("owner-a",id,complete);
        assertEquals(rev.get("id"),((Map<?,?>)service.complete("owner-a",id,complete)).get("id"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=?",id));
        var snapshot=(com.fasterxml.jackson.databind.JsonNode)rev.get("snapshot");
        assertEquals(1,snapshot.path("summary").path("UNCHECKED").asInt());
        assertEquals(1,snapshot.path("summary").path("ISSUE_OBSERVED").asInt());
        assertThrows(ResponseStatusException.class,()->service.save("owner-a",id,new InspectionService.Save(2,"","",List.of())));
        service.reopen("owner-a",id,2);service.save("owner-a",id,new InspectionService.Save(3,"Changed requirement","",List.of()));
        assertEquals("Works",((com.fasterxml.jackson.databind.JsonNode)((Map<?,?>)service.revision("owner-a",false,id,rev.get("id").toString())).get("snapshot")).path("userRequirement").asText());
        service.delete("owner-a",id,4);assertThrows(ResponseStatusException.class,()->service.detail("owner-a",false,id));
        assertEquals(2,db.count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=?",id));
    }
    @Test void creationRetryIsStableAndForeignItemSaveRollsBack() {
        var input=input();var row=service.create("owner-c",input);String id=row.get("id").toString();
        assertEquals(id,service.create("owner-c",input).get("id"));
        var other=service.create("owner-d",input());
        String foreign=((List<Map<String,Object>>)other.get("items")).get(0).get("id").toString();
        assertThrows(ResponseStatusException.class,()->service.save("owner-c",id,new InspectionService.Save(0,"","",List.of(new InspectionService.Result(foreign,"NO_ISSUE_OBSERVED","")))));
        assertEquals(0L,((Number)service.detail("owner-c",false,id).get("lockVersion")).longValue());
    }
    @Test void unavailableGuideDoesNotCreatePartialInspection() {
        when(guides.snapshot("product",null)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        long before=db.count("SELECT COUNT(*) FROM inspection");
        assertThrows(ResponseStatusException.class,()->service.create("owner-e",input()));assertEquals(before,db.count("SELECT COUNT(*) FROM inspection"));
    }
    @Test void completedRevisionPhotosAreProtectedAndAdminReadsAreAudited() throws Exception {
        var row=service.create("photo-owner",input());String id=row.get("id").toString();String item=((List<Map<String,Object>>)row.get("items")).get(0).get("id").toString();
        var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);var stream=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",stream);
        var result=(Map<?,?>)attachments.upload("photo-owner",id,item,0,new org.springframework.mock.web.MockMultipartFile("file","photo.png","image/png",stream.toByteArray()));
        String file=result.get("id").toString();assertArrayEquals(stream.toByteArray(),attachments.download("photo-owner",false,file));
        assertThrows(ResponseStatusException.class,()->attachments.download("other-owner",false,file));
        attachments.download("admin",true,file);assertEquals(1,db.count("SELECT COUNT(*) FROM audit_log WHERE target_id=? AND action='ATTACHMENT_READ'",id));
        service.complete("photo-owner",id,new InspectionService.Complete(1,UUID.randomUUID().toString()));service.reopen("photo-owner",id,2);
        assertThrows(ResponseStatusException.class,()->attachments.delete("photo-owner",file,3));
        service.delete("photo-owner",id,3);assertThrows(ResponseStatusException.class,()->attachments.download("photo-owner",false,file));
    }
}
