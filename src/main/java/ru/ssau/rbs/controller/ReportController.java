package ru.ssau.rbs.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.dto.report.UtilizationResponse;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.ReportService;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/utilization")
    public ResponseEntity<?> utilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "group_by", required = false) String groupBy,
            @RequestParam(required = false, defaultValue = "json") String format,
            @AuthenticationPrincipal UserPrincipal principal) {
        UtilizationResponse report = reportService.utilization(from, to, groupBy, principal);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"utilization.csv\"")
                    .body(reportService.toCsv(report));
        }
        return ResponseEntity.ok(report);
    }
}
