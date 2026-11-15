package ru.ssau.rbs.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.booking.BookingCreateRequest;
import ru.ssau.rbs.dto.booking.BookingMoveRequest;
import ru.ssau.rbs.dto.booking.BookingResponse;
import ru.ssau.rbs.dto.booking.BookingResult;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.BookingService;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingCreateRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        BookingResult result = bookingService.create(request, idempotencyKey, principal);
        HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.getResponse());
    }

    @GetMapping("/mine")
    public PageResponse<BookingResponse> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.mine(principal, status, from, to, limit, offset);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageResponse<BookingResponse> list(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "department_id", required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getRole() == UserRole.MANAGER) {
            return bookingService.list(userId, principal.getDepartmentId(), status, from, to, limit, offset);
        }
        return bookingService.list(userId, departmentId, status, from, to, limit, offset);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.get(id, principal);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.cancel(id, principal);
    }

    @PostMapping("/{id}/move")
    public BookingResponse move(@PathVariable Long id,
                                @Valid @RequestBody BookingMoveRequest request,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.move(id, request, principal);
    }
}
