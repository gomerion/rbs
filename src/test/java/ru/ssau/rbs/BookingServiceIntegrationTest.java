package ru.ssau.rbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.ssau.rbs.domain.entity.Booking;
import ru.ssau.rbs.domain.entity.Department;
import ru.ssau.rbs.domain.entity.ExternalBusy;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.audit.AuditLogResponse;
import ru.ssau.rbs.dto.booking.BookingCreateRequest;
import ru.ssau.rbs.dto.booking.BookingMoveRequest;
import ru.ssau.rbs.dto.booking.BookingResult;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.dto.resource.WorkingHoursDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.DepartmentRepository;
import ru.ssau.rbs.repository.ExternalBusyRepository;
import ru.ssau.rbs.repository.ResourceRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.AuditService;
import ru.ssau.rbs.service.BookingService;
import ru.ssau.rbs.service.ResourceService;

@SpringBootTest(properties = {
        "app.booking.max-active-employee=2",
        "app.booking.cancel-hours-employee=48",
        "app.booking.max-duration-minutes=180",
        "app.booking.advance-days=30"
})
@Testcontainers(disabledWithoutDocker = true)
class BookingServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private static final ZoneId SAMARA = ZoneId.of("Europe/Samara");

    @Autowired
    BookingService bookingService;
    @Autowired
    ResourceService resourceService;
    @Autowired
    AuditService auditService;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    ResourceRepository resourceRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    ExternalBusyRepository externalBusyRepository;

    @Test
    void createOnSelfReturnsConfirmedBooking() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        BookingResult result = bookingService.create(request(resource.getId(), day(3), 10, 12), null,
                principal(employee, null));

        assertFalse(result.isReplayed());
        assertEquals("confirmed", result.getResponse().getStatus());
        assertEquals("pending", result.getResponse().getSyncStatus());
        assertEquals(employee.getId(), result.getResponse().getUserId());

        List<AuditLogResponse> audit = auditService.list(employee.getId(), "booking.create", null, null,
                null, null, 50, 0).getItems();
        assertTrue(audit.stream().anyMatch(item -> String.valueOf(result.getResponse().getId()).equals(item.getObjectId())));
    }

    @Test
    void createRejectsInvalidIntervals() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);

        assertEquals("slot_alignment", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(3), 10, 11, 7), null, actor)).getCode());
        assertEquals("duration_out_of_range", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(3), 9, 14), null, actor)).getCode());
        assertEquals("outside_working_hours", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(3), 6, 8), null, actor)).getCode());
        assertEquals("advance_limit", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(40), 10, 11), null, actor)).getCode());
        assertEquals("booking_in_past", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(-2), 10, 11), null, actor)).getCode());
    }

    @Test
    void createRejectsArchivedResourceAndExternalBusy() {
        ResourceResponse archived = resource();
        resourceService.archive(archived.getId());
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        assertEquals("resource_archived", assertThrows(ApiException.class, () ->
                bookingService.create(request(archived.getId(), day(4), 10, 11), null, actor)).getCode());

        ResourceResponse busy = resource();
        externalBusy(busy.getId(), at(day(5), 10), at(day(5), 11));
        assertEquals("external_busy", assertThrows(ApiException.class, () ->
                bookingService.create(request(busy.getId(), day(5), 10, 11), null, actor)).getCode());
    }

    @Test
    void createEnforcesEmployeeActiveLimit() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);

        bookingService.create(request(resource.getId(), day(6), 8, 9), null, actor);
        bookingService.create(request(resource.getId(), day(6), 9, 10), null, actor);

        assertEquals("active_limit", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(6), 10, 11), null, actor)).getCode());
    }

    @Test
    void managerBooksForOwnDepartmentOnly() {
        Department own = department();
        Department foreign = department();
        ResourceResponse resource = resource();
        User manager = user(UserRole.MANAGER, own);
        User subordinate = user(UserRole.EMPLOYEE, own);
        User stranger = user(UserRole.EMPLOYEE, foreign);
        UserPrincipal actor = principal(manager, own.getId());

        BookingResult result = bookingService.create(request(resource.getId(), day(7), 10, 11, subordinate.getId()),
                null, actor);
        assertEquals(subordinate.getId(), result.getResponse().getUserId());
        assertEquals(manager.getId(), result.getResponse().getCreatedById());

        assertEquals("forbidden", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(7), 11, 12, stranger.getId()), null, actor)).getCode());

        UserPrincipal employee = principal(subordinate, own.getId());
        assertEquals("forbidden", assertThrows(ApiException.class, () ->
                bookingService.create(request(resource.getId(), day(7), 12, 13, stranger.getId()), null, employee)).getCode());
    }

    @Test
    void idempotencyKeyReplaysSameBooking() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        String key = "key-" + SEQ.incrementAndGet();

        BookingResult first = bookingService.create(request(resource.getId(), day(8), 10, 11), key, actor);
        BookingResult second = bookingService.create(request(resource.getId(), day(8), 10, 11), key, actor);

        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed());
        assertEquals(first.getResponse().getId(), second.getResponse().getId());
        assertEquals(1, bookingRepository.findByUserIdAndIdempotencyKey(employee.getId(), key).stream().count());
    }

    @Test
    void cancelWithinWindowThenTwiceFails() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        Long bookingId = bookingService.create(request(resource.getId(), day(20), 10, 11), null, actor)
                .getResponse().getId();

        assertEquals("cancelled", bookingService.cancel(bookingId, actor).getStatus());
        assertEquals("already_cancelled", assertThrows(ApiException.class, () ->
                bookingService.cancel(bookingId, actor)).getCode());
    }

    @Test
    void cancelOutsideWindowFails() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        Long bookingId = bookingService.create(request(resource.getId(), day(1), 9, 10), null, actor)
                .getResponse().getId();

        assertEquals("cancel_window_expired", assertThrows(ApiException.class, () ->
                bookingService.cancel(bookingId, actor)).getCode());
    }

    @Test
    void employeeCannotTouchForeignBookingButManagerCanInDepartment() {
        Department own = department();
        Department foreign = department();
        ResourceResponse resource = resource();
        User owner = user(UserRole.EMPLOYEE, own);
        User colleague = user(UserRole.EMPLOYEE, own);
        User manager = user(UserRole.MANAGER, own);
        User strangerManager = user(UserRole.MANAGER, foreign);

        Long bookingId = bookingService.create(request(resource.getId(), day(20), 10, 11), null,
                principal(owner, own.getId())).getResponse().getId();

        UserPrincipal colleagueActor = principal(colleague, own.getId());
        assertEquals("not_found", assertThrows(ApiException.class, () ->
                bookingService.cancel(bookingId, colleagueActor)).getCode());
        assertEquals("not_found", assertThrows(ApiException.class, () ->
                bookingService.get(bookingId, colleagueActor)).getCode());
        assertTrue(auditService.list(colleague.getId(), "booking.cancel", "booking", null, null, null, 50, 0)
                .getTotal() > 0);
        assertEquals("not_found", assertThrows(ApiException.class, () ->
                bookingService.cancel(bookingId, principal(strangerManager, foreign.getId()))).getCode());

        assertEquals("cancelled", bookingService.cancel(bookingId, principal(manager, own.getId())).getStatus());
    }

    @Test
    void moveKeepsIdentifierAndRejectsDurationChange() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        Long bookingId = bookingService.create(request(resource.getId(), day(9), 10, 12), null, actor)
                .getResponse().getId();

        var moved = bookingService.move(bookingId, new BookingMoveRequest(at(day(9), 14), at(day(9), 16)), actor);
        assertEquals(bookingId, moved.getId());
        assertEquals("moved", moved.getStatus());
        assertEquals(at(day(9), 14), moved.getStartsAt());

        assertEquals("validation_error", assertThrows(ApiException.class, () ->
                bookingService.move(bookingId, new BookingMoveRequest(at(day(9), 16), at(day(9), 17)), actor)).getCode());
    }

    @Test
    void moveIntoBusySlotKeepsOldInterval() {
        ResourceResponse resource = resource();
        User employee = user(UserRole.EMPLOYEE, department());
        UserPrincipal actor = principal(employee, null);
        Long first = bookingService.create(request(resource.getId(), day(10), 10, 11), null, actor)
                .getResponse().getId();
        bookingService.create(request(resource.getId(), day(10), 12, 13), null, actor);

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.move(first, new BookingMoveRequest(at(day(10), 12), at(day(10), 13)), actor));

        assertEquals("conflict", exception.getCode());
        assertTrue(exception.getProperties().containsKey("conflicting_booking_id"));
        Booking stored = bookingRepository.findById(first).orElseThrow();
        assertEquals(at(day(10), 10), stored.getStartsAt());
        assertEquals(BookingStatus.CONFIRMED, stored.getStatus());
    }

    @Test
    void mineReturnsOnlyOwnBookings() {
        ResourceResponse resource = resource();
        User first = user(UserRole.EMPLOYEE, department());
        User second = user(UserRole.EMPLOYEE, department());
        Long bookingId = bookingService.create(request(resource.getId(), day(11), 10, 11), null,
                principal(first, null)).getResponse().getId();
        bookingService.create(request(resource.getId(), day(11), 11, 12), null, principal(second, null));

        var mine = bookingService.mine(principal(first, null), null, null, null, 50, 0);
        assertEquals(1, mine.getTotal());
        assertEquals(bookingId, mine.getItems().getFirst().getId());
    }

    private ResourceResponse resource() {
        String code = "res-" + SEQ.incrementAndGet();
        List<WorkingHoursDto> hours = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            hours.add(new WorkingHoursDto(weekday, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        }
        ResourceCreateRequest request = new ResourceCreateRequest(code, "Ресурс " + code,
                SEQ.get() % 2 == 0 ? "room" : "equipment", 8, "Корпус", List.of(), "Europe/Samara", hours, null);
        return resourceService.create(request);
    }

    private Department department() {
        Department department = new Department();
        department.setName("Кафедра-" + SEQ.incrementAndGet());
        return departmentRepository.saveAndFlush(department);
    }

    private User user(UserRole role, Department department) {
        User user = new User();
        user.setEmail("user" + SEQ.incrementAndGet() + "@example.ru");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$hash");
        user.setRole(role);
        user.setDepartment(department);
        return userRepository.saveAndFlush(user);
    }

    private static UserPrincipal principal(User user, Long departmentId) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), departmentId);
    }

    private void externalBusy(Long resourceId, Instant startsAt, Instant endsAt) {
        ExternalBusy busy = new ExternalBusy();
        busy.setResource(resourceRepository.getReferenceById(resourceId));
        busy.setExternalId("ext-" + SEQ.incrementAndGet());
        busy.setStartsAt(startsAt);
        busy.setEndsAt(endsAt);
        externalBusyRepository.saveAndFlush(busy);
    }

    private static LocalDate day(int plusDays) {
        return LocalDate.now(SAMARA).plusDays(plusDays);
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(SAMARA).toInstant();
    }

    private static BookingCreateRequest request(Long resourceId, LocalDate day, int fromHour, int toHour) {
        return new BookingCreateRequest(resourceId, at(day, fromHour), at(day, toHour), "Совещание", null);
    }

    private static BookingCreateRequest request(Long resourceId, LocalDate day, int fromHour, int toHour,
                                                int minute) {
        return new BookingCreateRequest(resourceId, day.atTime(fromHour, minute).atZone(SAMARA).toInstant(),
                at(day, toHour), "Совещание", null);
    }

    private static BookingCreateRequest request(Long resourceId, LocalDate day, int fromHour, int toHour,
                                                Long userId) {
        return new BookingCreateRequest(resourceId, at(day, fromHour), at(day, toHour), "Совещание", userId);
    }
}
