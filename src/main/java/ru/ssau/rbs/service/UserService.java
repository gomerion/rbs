package ru.ssau.rbs.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.user.UserDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Sort BY_EMAIL = Sort.by(Sort.Order.asc("email"));

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(Integer limit, Integer offset) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        int skip = offset == null ? 0 : offset;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.validationError("limit: допустимое значение от 1 до " + MAX_LIMIT);
        }
        if (skip < 0) {
            throw ApiException.validationError("offset: не может быть отрицательным");
        }
        Page<User> page = userRepository.findAll(PageRequest.of(skip / size, size, BY_EMAIL));
        List<Long> ids = page.getContent().stream().map(User::getId).toList();
        Map<Long, Long> departments = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : userRepository.findDepartmentIds(ids)) {
                departments.put((Long) row[0], (Long) row[1]);
            }
        }
        List<UserDto> items = page.getContent().stream()
                .map(user -> toDto(user, departments.get(user.getId())))
                .toList();
        return PageResponse.of(items, page.getTotalElements(), size, skip);
    }

    @Transactional
    public UserDto changeRole(Long id, String role, Long actorId) {
        UserRole target = parseRole(role);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден: " + id));
        String before = user.getRole().getCode();
        user.setRole(target);
        User saved = userRepository.saveAndFlush(user);
        auditService.record(actorId, "user.role_change", "user", id,
                Map.of("role", before), Map.of("role", saved.getRole().getCode()));
        Long departmentId = userRepository.findDepartmentIdById(id).orElse(null);
        return toDto(saved, departmentId);
    }

    private static UserRole parseRole(String value) {
        try {
            return UserRole.fromCode(value.trim().toLowerCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw ApiException.validationError("role: допустимы employee, manager, admin");
        }
    }

    private static UserDto toDto(User user, Long departmentId) {
        return new UserDto(user.getId(), user.getEmail(), user.getRole().getCode(), departmentId);
    }
}
