package ru.ssau.rbs.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.resource.AvailabilityResponse;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourcePatchRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.service.ResourceService;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public PageResponse<ResourceResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(name = "min_capacity", required = false) Integer minCapacity,
            @RequestParam(required = false) List<String> features,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return resourceService.search(type, minCapacity, features, location, status, sort, limit, offset);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return resourceService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ResourceResponse create(@Valid @RequestBody ResourceCreateRequest request) {
        return resourceService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResourceResponse update(@PathVariable Long id, @Valid @RequestBody ResourcePatchRequest request) {
        return resourceService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResourceResponse archive(@PathVariable Long id) {
        return resourceService.archive(id);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return resourceService.availability(id, from, to);
    }
}
