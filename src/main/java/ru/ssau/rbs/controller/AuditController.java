package ru.ssau.rbs.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.dto.audit.AuditLogResponse;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.service.AuditService;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @RequestParam(name = "actor_id", required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(name = "object_type", required = false) String objectType,
            @RequestParam(name = "object_id", required = false) String objectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return auditService.list(actorId, action, objectType, objectId, from, to, limit, offset);
    }

    @GetMapping("/{id}")
    public AuditLogResponse get(@PathVariable Long id) {
        return auditService.get(id);
    }
}
