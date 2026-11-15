package ru.ssau.rbs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.user.RoleUpdateRequest;
import ru.ssau.rbs.dto.user.UserDto;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) Integer offset) {
        return userService.list(limit, offset);
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(@PathVariable Long id,
                              @Valid @RequestBody RoleUpdateRequest request,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return userService.changeRole(id, request.getRole(), principal.getId());
    }
}
