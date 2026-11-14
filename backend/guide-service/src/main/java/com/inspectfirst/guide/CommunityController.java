package com.inspectfirst.guide;

import com.inspectfirst.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
public class CommunityController {
    private final CommunityService service;private final SessionAccess auth;
    public CommunityController(CommunityService service,SessionAccess auth) { this.service=service;this.auth=auth; }
    @GetMapping("/api/saved-guides") Object saved(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { return service.saved(auth.require(r,false).id(),page,size); }
    @PutMapping("/api/saved-guides/{id}") Object save(@PathVariable String id,HttpServletRequest r) { return service.save(auth.require(r,false).id(),id); }
    @DeleteMapping("/api/saved-guides/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void unsave(@PathVariable String id,HttpServletRequest r) { service.unsave(auth.require(r,false).id(),id); }
    @PostMapping("/api/saved-guides/{id}/seen") Object seen(@PathVariable String id,HttpServletRequest r) { return service.seen(auth.require(r,false).id(),id); }
    @PostMapping("/api/feedback") Object create(@Valid @RequestBody CommunityService.FeedbackInput in,HttpServletRequest r) { return service.create(auth.require(r,false).id(),in); }
    @GetMapping("/api/feedback") Object mine(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { return service.list(auth.require(r,false).id(),false,status,type,page,size); }
    @GetMapping("/api/feedback/{id}") Object detail(@PathVariable String id,HttpServletRequest r) { return service.detail(auth.require(r,false).id(),false,id); }
    @PostMapping("/api/feedback/{id}/replies") Object reply(@PathVariable String id,@Valid @RequestBody CommunityService.Reply in,HttpServletRequest r) { return service.reply(auth.require(r,false).id(),id,in); }
    @GetMapping("/api/ops/feedback") Object inbox(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r) { return service.list(auth.require(r,true).id(),true,status,type,page,size); }
    @GetMapping("/api/ops/feedback/{id}") Object staffDetail(@PathVariable String id,HttpServletRequest r) { return service.detail(auth.require(r,true).id(),true,id); }
    @PatchMapping("/api/ops/feedback/{id}/status") Object status(@PathVariable String id,@Valid @RequestBody CommunityService.Status in,HttpServletRequest r) { return service.status(auth.require(r,true).id(),id,in); }
}
