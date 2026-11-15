package ru.ssau.rbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.ssau.rbs.controller.AuditController;
import ru.ssau.rbs.controller.UserController;
import ru.ssau.rbs.domain.entity.Department;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.audit.AuditLogResponse;
import ru.ssau.rbs.dto.booking.BookingCreateRequest;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.report.UtilizationItem;
import ru.ssau.rbs.dto.report.UtilizationResponse;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.dto.resource.WorkingHoursDto;
import ru.ssau.rbs.dto.user.UserDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.DepartmentRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.AuditService;
import ru.ssau.rbs.service.BookingService;
import ru.ssau.rbs.service.ReportService;
import ru.ssau.rbs.service.ResourceService;
import ru.ssau.rbs.service.UserService;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReportAuditUserIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private static final ZoneId SAMARA = ZoneId.of("Europe/Samara");

    @Autowired
    ReportService reportService;
    @Autowired
    AuditService auditService;
    @Autowired
    UserService userService;
    @Autowired
    BookingService bookingService;
    @Autowired
    ResourceService resourceService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    AuditController auditController;
    @Autowired
    UserController userController;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportGroupsByResourceAndRespectsDepartmentScope() {
        Department own = department();
        Department foreign = department();
        ResourceResponse first = resource("room");
        ResourceResponse second = resource("equipment");
        User manager = user(UserRole.MANAGER, own);
        User subordinate = user(UserRole.EMPLOYEE, own);
        User stranger = user(UserRole.EMPLOYEE, foreign);
        LocalDate day = LocalDate.now(SAMARA).plusDays(2);

        book(first, subordinate, own, day, 10, 12);
        book(second, stranger, foreign, day, 10, 11);

        UtilizationResponse ownReport = reportService.utilization(periodFrom(day), periodTo(day), "resource",
                principal(manager, own.getId()));
        assertEquals("resource", ownReport.getGroupBy());
        assertEquals(1, ownReport.getItems().size());
        UtilizationItem item = ownReport.getItems().getFirst();
        assertEquals(first.getCode(), item.getKey());
        assertEquals(1, item.getBookings());
        assertEquals(2.0, item.getBusyHours());
        assertEquals(0.167, item.getUtilizationRatio());
        assertEquals(1, ownReport.getTop5().size());

        UtilizationResponse all = reportService.utilization(periodFrom(day), periodTo(day), "resource",
                principal(user(UserRole.ADMIN, own), null));
        assertEquals(2, all.getItems().size());
        assertTrue(all.getItems().stream().anyMatch(entry -> entry.getKey().equals(second.getCode())));

        assertEquals("forbidden", assertThrows(ApiException.class, () -> reportService.utilization(
                periodFrom(day), periodTo(day), "resource", principal(subordinate, own.getId()))).getCode());
    }

    @Test
    void csvCarriesSameValuesAsJson() {
        Department department = department();
        ResourceResponse resource = resource("room");
        User employee = user(UserRole.EMPLOYEE, department);
        User admin = user(UserRole.ADMIN, department);
        LocalDate day = LocalDate.now(SAMARA).plusDays(3);

        book(resource, employee, department, day, 9, 11);

        UtilizationResponse report = reportService.utilization(periodFrom(day), periodTo(day), "resource",
                principal(admin, null));
        String csv = reportService.toCsv(report);
        String[] lines = csv.strip().split("\n");

        assertEquals("key,bookings,busy_hours,utilization_ratio", lines[0]);
        UtilizationItem item = report.getItems().getFirst();
        assertEquals(item.getKey() + ",1,2.0,0.167", lines[1]);
    }

    @Test
    void auditIsVisibleToAdminOnly() {
        Department department = department();
        ResourceResponse resource = resource("room");
        User admin = user(UserRole.ADMIN, department);
        User employee = user(UserRole.EMPLOYEE, department);
        LocalDate day = LocalDate.now(SAMARA).plusDays(4);
        book(resource, employee, department, day, 10, 11);

        PageResponse<AuditLogResponse> records = auditService.list(null, "booking.create", "booking", null,
                null, null, 50, 0);
        assertTrue(records.getTotal() > 0);
        AuditLogResponse record = records.getItems().getFirst();
        assertNotNull(record.getAfterState());
        assertNotNull(record.getRequestId());
        assertEquals(record.getId(), auditService.get(record.getId()).getId());

        runAs(principal(admin, department.getId()));
        assertTrue(auditController.list(null, null, null, null, null, null, 50, 0).getTotal() > 0);

        runAs(principal(employee, department.getId()));
        assertThrows(AccessDeniedException.class, () ->
                auditController.list(null, null, null, null, null, null, 50, 0));
    }

    @Test
    void adminChangesRoleAndRecordsAudit() {
        Department department = department();
        User admin = user(UserRole.ADMIN, department);
        User employee = user(UserRole.EMPLOYEE, department);

        runAs(principal(admin, department.getId()));
        UserDto promoted = userController.changeRole(employee.getId(),
                new ru.ssau.rbs.dto.user.RoleUpdateRequest("manager"), principal(admin, department.getId()));
        assertEquals("manager", promoted.getRole());
        assertEquals(department.getId(), promoted.getDepartmentId());

        PageResponse<UserDto> users = userController.list(100, 0);
        assertTrue(users.getItems().stream()
                .anyMatch(item -> item.getId().equals(employee.getId()) && "manager".equals(item.getRole())));

        List<AuditLogResponse> audit = auditService.list(admin.getId(), "user.role_change", "user",
                null, null, null, 50, 0).getItems();
        assertFalse(audit.isEmpty());
        assertEquals("employee", audit.getFirst().getBeforeState().get("role"));
        assertEquals("manager", audit.getFirst().getAfterState().get("role"));

        runAs(principal(employee, department.getId()));
        assertThrows(AccessDeniedException.class, () -> userController.list(10, 0));
        assertEquals("validation_error", assertThrows(ApiException.class, () ->
                userService.changeRole(employee.getId(), "superuser", employee.getId())).getCode());
    }

    private void runAs(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void book(ResourceResponse resource, User owner, Department department, LocalDate day,
                      int fromHour, int toHour) {
        Instant startsAt = day.atTime(fromHour, 0).atZone(SAMARA).toInstant();
        Instant endsAt = day.atTime(toHour, 0).atZone(SAMARA).toInstant();
        bookingService.create(new BookingCreateRequest(resource.getId(), startsAt, endsAt, "Совещание", null),
                null, principal(owner, department.getId()));
    }

    private ResourceResponse resource(String type) {
        String code = "rep-" + SEQ.incrementAndGet();
        List<WorkingHoursDto> hours = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            hours.add(new WorkingHoursDto(weekday, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        }
        return resourceService.create(new ResourceCreateRequest(code, "Ресурс " + code, type, 6, "Корпус",
                List.of(), "Europe/Samara", hours, null));
    }

    private Department department() {
        Department department = new Department();
        department.setName("Кафедра-" + SEQ.incrementAndGet());
        return departmentRepository.saveAndFlush(department);
    }

    private User user(UserRole role, Department department) {
        User user = new User();
        user.setEmail("rep" + SEQ.incrementAndGet() + "@example.ru");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$hash");
        user.setRole(role);
        user.setDepartment(department);
        return userRepository.saveAndFlush(user);
    }

    private static UserPrincipal principal(User user, Long departmentId) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), departmentId);
    }

    private static Instant periodFrom(LocalDate day) {
        return day.atStartOfDay(SAMARA).toInstant();
    }

    private static Instant periodTo(LocalDate day) {
        return day.plusDays(1).atStartOfDay(SAMARA).toInstant();
    }
}
