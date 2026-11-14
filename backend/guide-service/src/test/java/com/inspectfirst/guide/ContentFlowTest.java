package com.inspectfirst.guide;
import com.inspectfirst.shared.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:contentflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ContentFlowTest {
    @Autowired ContentService content;@Autowired CommunityService community;@Autowired Store db;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.inspectfirst.shared.SessionAccess auth;
    @Test void administratorReadEndpointsWorkWithTransactionalServiceProxy() throws Exception {
        org.mockito.Mockito.when(auth.require(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(true)))
            .thenReturn(new com.inspectfirst.shared.SessionAccess.Actor("admin","admin@example.test","Admin","ADMIN"));
        for(String path:List.of("/api/ops/content/categories","/api/ops/content/stats","/api/ops/content/audit","/api/ops/content/guides"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
    ContentService.Draft input(long version,String url) {
        return new ContentService.Draft(version,"General inspection","Permission and known-working accessories",LocalDate.now(),"Not an authenticity guarantee.",List.of(new ContentService.Item("Observe","Look for issues",List.of("Inspect with permission"),"No issue observed","Visible damage","Cannot prove hidden condition",List.of(new ContentService.Source("Manual",url,"Maker")))));
    }
    Map<String,Object> draft() {
        var c=content.category(null,new ContentService.CategoryInput("cat-"+UUID.randomUUID().toString().replaceAll("[0-9]","a"),"Test category"),"admin");
        return content.draft(new ContentService.NewGuide(null,c.get("id").toString()),"admin");
    }
    @Test void versionLifecycleSourcesAndImmutablePublishedContent() {
        var v=draft();String version=v.get("versionId").toString(),guide=v.get("id").toString();
        assertThrows(ResponseStatusException.class,()->content.version(version,true));
        assertThrows(ResponseStatusException.class,()->content.publish(version,0,"admin"));
        assertThrows(ResponseStatusException.class,()->content.save(version,input(0,"javascript:alert(1)"),"admin"));
        content.save(version,input(0,"https://example.com/manual"),"admin");
        assertThrows(ResponseStatusException.class,()->content.save(version,input(0,"https://example.com/manual"),"admin"));
        content.publish(version,1,"admin");
        assertEquals("CATEGORY",content.version(version,true).get("scope"));
        assertThrows(ResponseStatusException.class,()->content.save(version,input(2,"https://example.com/manual"),"admin"));
        content.withdraw(guide,"Source needs review","admin");
        assertThrows(ResponseStatusException.class,()->content.version(version,true));
        assertEquals("WITHDRAWN",content.version(version,false).get("status"));
    }
    @Test void exampleImagesPersistWithVersionAndRejectUnsafePaths() {
        var v=draft();String version=v.get("versionId").toString();
        var base=input(0,"https://example.com/manual");var item=base.items().get(0);
        var image=new ContentService.Illustration("/images/checks/screen-example.svg","Screen diagram","Editorial schematic, not a real screenshot.","DIAGRAM","https://example.com/manual","Reference manual");
        var illustrated=new ContentService.Item(item.title(),item.purpose(),item.steps(),item.expectedObservation(),item.abnormalSigns(),item.limitations(),item.sources(),List.of(image));
        var full=new ContentService.Draft(0,base.title(),base.tools(),base.reviewedOn(),base.contentNote(),List.of(illustrated));
        content.save(version,full,"admin");content.publish(version,1,"admin");
        var checks=(List<Map<String,Object>>)content.version(version,true).get("items");
        var images=(List<Map<String,Object>>)checks.get(0).get("images");
        assertEquals(1,images.size());assertEquals(image.url(),images.get(0).get("url"));assertEquals("DIAGRAM",images.get(0).get("kind"));
        assertThrows(ResponseStatusException.class,()->content.save(version,full,"admin"));
        var other=draft();String otherVersion=other.get("versionId").toString();
        var unsafe=new ContentService.Illustration("/images/checks/../private.svg","Unsafe","Unsafe","DIAGRAM","https://example.com/manual","Manual");
        var invalidItem=new ContentService.Item(item.title(),item.purpose(),item.steps(),item.expectedObservation(),item.abnormalSigns(),item.limitations(),item.sources(),List.of(unsafe));
        var invalid=new ContentService.Draft(0,base.title(),base.tools(),base.reviewedOn(),base.contentNote(),List.of(invalidItem));
        assertThrows(ResponseStatusException.class,()->content.save(otherVersion,invalid,"admin"));
    }
    @Test void savedGuidesAreOwnerScopedIdempotentAndRetainWithdrawnMetadata() {
        String guide="30000000-0000-0000-0000-000000000001",owner=UUID.randomUUID().toString();
        community.save(owner,guide);community.save(owner,guide);
        assertEquals(1,db.count("SELECT COUNT(*) FROM saved_guide WHERE owner_user_id=?",owner));
        community.unsave("other-user",guide);assertEquals(1,db.count("SELECT COUNT(*) FROM saved_guide WHERE owner_user_id=?",owner));
        community.unsave(owner,guide);assertEquals(0,db.count("SELECT COUNT(*) FROM saved_guide WHERE owner_user_id=?",owner));
    }
    @Test void feedbackOwnershipDedupAndStateTransitions() {
        var input=new CommunityService.FeedbackInput(UUID.randomUUID().toString(),"MISSING_PRODUCT",null,null,null,"Missing model","Please add a sourced guide.");
        var first=(Map<?,?>)community.create("student-a",input);String id=first.get("id").toString();
        assertEquals(id,((Map<?,?>)community.create("student-a",input)).get("id"));
        assertThrows(ResponseStatusException.class,()->community.detail("student-b",false,id));
        assertThrows(ResponseStatusException.class,()->community.status("admin",id,new CommunityService.Status("RESOLVED","Done",0)));
        community.status("admin",id,new CommunityService.Status("IN_REVIEW","Reviewing",0));
        community.status("admin",id,new CommunityService.Status("NEED_DETAILS","Which model?",1));
        community.reply("student-a",id,new CommunityService.Reply("Model A",2));
        assertEquals("IN_REVIEW",community.detail("student-a",false,id).get("status"));
        assertThrows(ResponseStatusException.class,()->community.reply("student-a",id,new CommunityService.Reply("Stale",0)));
    }
}
