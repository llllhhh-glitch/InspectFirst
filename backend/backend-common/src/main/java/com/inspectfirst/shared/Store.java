package com.inspectfirst.shared;

import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Component
public class Store {
    public final JdbcTemplate jdbc;
    public final ObjectMapper json;
    public Store(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            Map<String,Object> row=new LinkedHashMap<>(); var md=rs.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++) {
                String raw=md.getColumnLabel(i).toLowerCase(Locale.ROOT); StringBuilder key=new StringBuilder(); boolean up=false;
                for(char c:raw.toCharArray()) { if(c=='_') up=true; else { key.append(up?Character.toUpperCase(c):c); up=false; } }
                Object value=rs.getObject(i);
                if(value instanceof java.sql.Clob c) value=c.getSubString(1,(int)c.length());
                if(value instanceof java.sql.Timestamp t) value=t.toInstant().toString();
                if(value instanceof java.sql.Date d) value=d.toLocalDate().toString();
                row.put(key.toString(),value);
            } return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        return rows(sql,args).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found."));
    }
    public long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("JSON serialization failed",e); }
    }
    public JsonNode decode(Object value) {
        try { return json.readTree(value.toString()); } catch(Exception e) { throw new IllegalStateException("Stored JSON is invalid",e); }
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public static void check(boolean condition,int status,String message) {
        if(!condition) throw new ResponseStatusException(HttpStatus.valueOf(status),message);
    }
    public static int offset(int page,int size) {
        check(page>=0&&page<=10000&&size>=1&&size<=100,400,"Invalid pagination."); return page*size;
    }
    public static String like(String text) {
        check(text!=null&&text.length()<=100,400,"Search is too long.");
        return "%"+text.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    public Map<String,Object> page(List<?> rows,long total,int page,int size) {
        return Map.of("items",rows,"total",total,"page",page,"size",size,"hasNext",((long)page+1)*size<total);
    }
    public void audit(String actor,String action,String target,Object details) {
        update("INSERT INTO audit_log(id,actor_id,action,target_id,details) VALUES (?,?,?,?,?)",id(),actor,action,target,encode(details));
    }
}
