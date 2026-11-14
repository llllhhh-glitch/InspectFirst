package com.inspectfirst.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/ops/users")
public class UserManagement {
    private final JdbcTemplate db;
    public UserManagement(JdbcTemplate db) { this.db=db; }
    record UserRow(String id,String email,String displayName,String role,String status,String createdAt) {}
    record Status(@Pattern(regexp="ACTIVE|DISABLED") String status) {}
    List<UserRow> query(String suffix,Object... args) {
        return db.query("SELECT id,email,display_name,role,status,created_at FROM user_account "+suffix,
            (rs,n)->new UserRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant().toString()),args);
    }
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(q.length()>100||page<0||page>10000||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid search or pagination.");
        String term="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where="WHERE LOWER(email) LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!'";
        long count=db.queryForObject("SELECT COUNT(*) FROM user_account "+where,Long.class,term,term);
        return Map.of("items",query(where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",term,term,size,page*size),"total",count,"page",page,"size",size);
    }
    @GetMapping("/{id}") UserRow detail(@PathVariable String id) {
        return query("WHERE id=?",id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
    }
    @GetMapping("/audit") Object audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(page<0||page>10000||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination.");
        var items=db.query("SELECT id,actor_id,action,target_id,created_at FROM identity_audit ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
            (rs,n)->Map.of("id",rs.getString(1),"actorId",rs.getString(2),"action",rs.getString(3),"targetId",rs.getString(4),"createdAt",rs.getTimestamp(5).toInstant().toString()),size,page*size);
        return Map.of("items",items,"total",db.queryForObject("SELECT COUNT(*) FROM identity_audit",Long.class),"page",page,"size",size);
    }
    @PatchMapping("/{id}/status") @Transactional UserRow status(@PathVariable String id,@Valid @RequestBody Status input,@AuthenticationPrincipal Accounts.Account actor) {
        if(input.status()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status required.");
        var row=detail(id);
        if(row.role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.CONFLICT,"The sole administrator cannot be disabled.");
        db.update("UPDATE user_account SET status=? WHERE id=?",input.status(),id);
        db.update("INSERT INTO identity_audit(id,actor_id,action,target_id) VALUES (?,?,?,?)",UUID.randomUUID().toString(),actor.view().id(),"USER_"+input.status(),id);
        return detail(id);
    }
}
