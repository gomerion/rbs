package ru.ssau.rbs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import ru.ssau.rbs.domain.entity.AuditLog;
import ru.ssau.rbs.domain.entity.Booking;
import ru.ssau.rbs.domain.entity.BookingSeries;
import ru.ssau.rbs.domain.entity.Department;
import ru.ssau.rbs.domain.entity.ExternalBusy;
import ru.ssau.rbs.domain.entity.LoginAttempt;
import ru.ssau.rbs.domain.entity.Reminder;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.entity.ResourceWorkingHour;
import ru.ssau.rbs.domain.entity.ResourceWorkingHourId;
import ru.ssau.rbs.domain.entity.SyncOutbox;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.BookingSyncStatus;
import ru.ssau.rbs.domain.enums.OutboxOperation;
import ru.ssau.rbs.domain.enums.OutboxStatus;
import ru.ssau.rbs.domain.enums.ReminderStatus;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.ResourceType;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.repository.AuditLogRepository;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.BookingSeriesRepository;
import ru.ssau.rbs.repository.DepartmentRepository;
import ru.ssau.rbs.repository.ExternalBusyRepository;
import ru.ssau.rbs.repository.LoginAttemptRepository;
import ru.ssau.rbs.repository.ReminderRepository;
import ru.ssau.rbs.repository.ResourceRepository;
import ru.ssau.rbs.repository.ResourceWorkingHourRepository;
import ru.ssau.rbs.repository.SyncOutboxRepository;
import ru.ssau.rbs.repository.UserRepository;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ResourceRepository resourceRepository;
    @Autowired
    ResourceWorkingHourRepository workingHourRepository;
    @Autowired
    BookingSeriesRepository seriesRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    ReminderRepository reminderRepository;
    @Autowired
    ExternalBusyRepository externalBusyRepository;
    @Autowired
    SyncOutboxRepository outboxRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    LoginAttemptRepository loginAttemptRepository;
    @Autowired
    PlatformTransactionManager transactionManager;

    private Department department(String name) {
        Department department = new Department();
        department.setName(name + "-" + SEQ.incrementAndGet());
        return departmentRepository.saveAndFlush(department);
    }

    private User user(Department department, UserRole role) {
        User user = new User();
        user.setEmail("User" + SEQ.incrementAndGet() + "@Example.RU");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$hash");
        user.setRole(role);
        user.setDepartment(department);
        return userRepository.saveAndFlush(user);
    }

    private Resource resource() {
        Resource resource = new Resource();
        long n = SEQ.incrementAndGet();
        resource.setCode("room-" + n);
        resource.setCalendarCode("cal-" + n);
        resource.setName("Переговорная 301");
        resource.setType(ResourceType.ROOM);
        resource.setCapacity(12);
        resource.setLocation("Корпус 3, этаж 3");
        resource.setFeatures(new String[]{"проектор", "кондиционер"});
        return resourceRepository.saveAndFlush(resource);
    }

    private Booking booking(Resource resource, User owner, Instant startsAt) {
        Booking booking = new Booking();
        booking.setResource(resource);
        booking.setUser(owner);
        booking.setCreatedBy(owner);
        booking.setStartsAt(startsAt);
        booking.setEndsAt(startsAt.plus(1, ChronoUnit.HOURS));
        booking.setPurpose("Совещание");
        booking.setIdempotencyKey("idem-" + SEQ.incrementAndGet());
        return bookingRepository.saveAndFlush(booking);
    }

    @Test
    void departmentAndUserRoundTrip() {
        Department department = department("Кафедра ИВТ");
        User manager = user(department, UserRole.MANAGER);

        assertNotNull(manager.getId());
        assertNotNull(manager.getCreatedAt());
        assertNotNull(manager.getUpdatedAt());
        assertEquals(UserRole.MANAGER, manager.getRole());

        assertTrue(departmentRepository.findByName(department.getName()).isPresent());
        assertEquals(1, userRepository.findAllByDepartmentId(department.getId()).size());
        assertEquals(1, userRepository.countByDepartmentId(department.getId()));
    }

    @Test
    void emailLookupIsCaseInsensitiveBecauseOfCitext() {
        User user = user(department("Кафедра ИВТ"), UserRole.EMPLOYEE);

        assertTrue(userRepository.findByEmail(user.getEmail().toLowerCase()).isPresent());
        assertTrue(userRepository.findByEmail(user.getEmail().toUpperCase()).isPresent());
        assertTrue(userRepository.existsByEmail(user.getEmail().toLowerCase()));
    }

    @Test
    void resourceRoundTripWithTextArrayAndDefaults() {
        Resource resource = resource();

        Resource loaded = resourceRepository.findByCode(resource.getCode()).orElseThrow();
        assertEquals(List.of("проектор", "кондиционер"), List.of(loaded.getFeatures()));
        assertEquals("Europe/Samara", loaded.getTimeZone());
        assertEquals(ResourceStatus.ACTIVE, loaded.getStatus());
        assertEquals(ResourceType.ROOM, loaded.getType());
        assertEquals(12, loaded.getCapacity());
        assertNotNull(loaded.getUpdatedAt());

        loaded.archive();
        resourceRepository.saveAndFlush(loaded);
        assertEquals(ResourceStatus.ARCHIVED,
                resourceRepository.findById(loaded.getId()).orElseThrow().getStatus());
    }

    @Test
    void resourceSearchByFeaturesAndFilters() {
        Resource resource = resource();

        assertTrue(codesOf(resourceRepository.findAllByFeatures("active", new String[]{"проектор"}))
                .contains(resource.getCode()));
        assertTrue(codesOf(resourceRepository.findAllByFeatures("active", new String[]{"проектор", "кондиционер"}))
                .contains(resource.getCode()));
        assertFalse(codesOf(resourceRepository.findAllByFeatures("active", new String[]{"проектор", "парковка"}))
                .contains(resource.getCode()));
        assertFalse(codesOf(resourceRepository.findAllByFeatures("archived", new String[]{"проектор"}))
                .contains(resource.getCode()));

        assertTrue(codesOf(resourceRepository.search(ResourceStatus.ACTIVE, ResourceType.ROOM, 5, "корпус 3"))
                .contains(resource.getCode()));
        assertTrue(codesOf(resourceRepository.search(ResourceStatus.ACTIVE, null, 1, null))
                .contains(resource.getCode()));
        assertFalse(codesOf(resourceRepository.search(ResourceStatus.ACTIVE, ResourceType.VEHICLE, 0, null))
                .contains(resource.getCode()));
        assertFalse(codesOf(resourceRepository.search(ResourceStatus.ACTIVE, null, 13, null))
                .contains(resource.getCode()));
    }

    @Test
    void workingHoursUseCompositeKey() {
        Resource resource = resource();
        ResourceWorkingHourId id = new ResourceWorkingHourId(resource.getId(), (short) 1, LocalTime.of(8, 0));

        ResourceWorkingHour morning = new ResourceWorkingHour();
        morning.setId(id);
        morning.setResource(resource);
        morning.setEndTime(LocalTime.of(13, 0));
        workingHourRepository.saveAndFlush(morning);

        ResourceWorkingHour afternoon = new ResourceWorkingHour();
        afternoon.setId(new ResourceWorkingHourId(resource.getId(), (short) 1, LocalTime.of(14, 0)));
        afternoon.setResource(resource);
        afternoon.setEndTime(LocalTime.of(20, 0));
        workingHourRepository.saveAndFlush(afternoon);

        List<ResourceWorkingHour> monday = workingHourRepository.findAllByResourceIdAndWeekday(resource.getId(), (short) 1);
        assertEquals(2, monday.size());
        assertEquals(LocalTime.of(8, 0), monday.getFirst().getId().getStartTime());
        assertEquals(LocalTime.of(20, 0), monday.getLast().getEndTime());
        assertEquals(2, workingHourRepository.findAllByResourceId(resource.getId()).size());
        assertTrue(monday.getFirst().isValidWindow());
    }

    @Test
    void bookingWithSeriesRoundTrip() {
        Department department = department("Кафедра ИВТ");
        User owner = user(department, UserRole.MANAGER);
        User creator = user(department, UserRole.ADMIN);
        Resource resource = resource();

        BookingSeries series = new BookingSeries();
        series.setResource(resource);
        series.setUser(owner);
        series.setWeeks(4);
        seriesRepository.saveAndFlush(series);

        Booking booking = booking(resource, owner, Instant.parse("2027-03-01T09:00:00Z"));
        booking.setCreatedBy(creator);
        booking.setSeries(series);
        bookingRepository.saveAndFlush(booking);

        Booking loaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, loaded.getStatus());
        assertEquals(BookingSyncStatus.PENDING, loaded.getSyncStatus());
        assertTrue(loaded.isActive());
        assertEquals(1, bookingRepository.findAllBySeriesId(series.getId()).size());
    }

    @Test
    void databaseRejectsOverlappingActiveBookings() {
        Resource resource = resource();
        User owner = user(department("Кафедра ИВТ"), UserRole.EMPLOYEE);
        Instant startsAt = Instant.parse("2027-03-01T09:00:00Z");
        Booking booking = booking(resource, owner, startsAt);

        Booking sameKey = new Booking();
        sameKey.setResource(resource);
        sameKey.setUser(owner);
        sameKey.setCreatedBy(owner);
        sameKey.setStartsAt(startsAt.plus(5, ChronoUnit.HOURS));
        sameKey.setEndsAt(startsAt.plus(6, ChronoUnit.HOURS));
        sameKey.setPurpose("Тот же ключ идемпотентности");
        sameKey.setIdempotencyKey(booking.getIdempotencyKey());
        assertThrows(DataIntegrityViolationException.class, () -> bookingRepository.saveAndFlush(sameKey));

        Booking overlapping = new Booking();
        overlapping.setResource(resource);
        overlapping.setUser(owner);
        overlapping.setCreatedBy(owner);
        overlapping.setStartsAt(startsAt.plus(30, ChronoUnit.MINUTES));
        overlapping.setEndsAt(startsAt.plus(90, ChronoUnit.MINUTES));
        overlapping.setPurpose("Пересечение");
        assertThrows(DataIntegrityViolationException.class, () -> bookingRepository.saveAndFlush(overlapping));

        assertFalse(bookingRepository.existsOverlapping(resource.getId(),
                startsAt.plus(1, ChronoUnit.HOURS), startsAt.plus(2, ChronoUnit.HOURS)));

        Booking current = bookingRepository.findById(booking.getId()).orElseThrow();
        current.cancel(Instant.now());
        bookingRepository.saveAndFlush(current);
        assertEquals(BookingStatus.CANCELLED,
                bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        assertNotNull(bookingRepository.findById(booking.getId()).orElseThrow().getCancelledAt());
        assertFalse(bookingRepository.existsOverlapping(resource.getId(), startsAt,
                startsAt.plus(1, ChronoUnit.HOURS)));

        overlapping.setStartsAt(startsAt);
        overlapping.setEndsAt(startsAt.plus(1, ChronoUnit.HOURS));
        bookingRepository.saveAndFlush(overlapping);
        assertTrue(bookingRepository.existsOverlapping(resource.getId(), startsAt, startsAt.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    void bookingQueriesByUserDepartmentAndPeriod() {
        Department department = department("Кафедра ИВТ");
        User owner = user(department, UserRole.EMPLOYEE);
        Resource resource = resource();
        Instant startsAt = Instant.parse("2027-03-01T09:00:00Z");
        booking(resource, owner, startsAt);

        Instant from = startsAt.minus(1, ChronoUnit.DAYS);
        Instant to = startsAt.plus(1, ChronoUnit.DAYS);

        assertEquals(1, bookingRepository.findByUserAndPeriod(owner.getId(), BookingStatus.ACTIVE, from, to).size());
        assertEquals(1, bookingRepository.findByDepartmentAndPeriod(department.getId(), BookingStatus.ACTIVE, from, to).size());
        assertEquals(1, bookingRepository.countByUserIdAndStatusIn(owner.getId(), BookingStatus.ACTIVE));
        assertEquals(1, bookingRepository.countByUserIdAndStatus(owner.getId(), BookingStatus.CONFIRMED));
        assertEquals(1, bookingRepository.countByResourceIdAndStatusIn(resource.getId(), BookingStatus.ACTIVE));
        assertEquals(1, bookingRepository.findOverlapping(resource.getId(), BookingStatus.ACTIVE,
                startsAt.plus(15, ChronoUnit.MINUTES), startsAt.plus(45, ChronoUnit.MINUTES)).size());

        Booking booking = bookingRepository.findByUserAndPeriod(owner.getId(), BookingStatus.ACTIVE, from, to).getFirst();
        booking.cancel(Instant.now());
        bookingRepository.saveAndFlush(booking);
        assertTrue(bookingRepository.findByUserAndPeriod(owner.getId(), BookingStatus.ACTIVE, from, to).isEmpty());
        assertEquals(1, bookingRepository.findByUserAndPeriod(owner.getId(), List.of(BookingStatus.CANCELLED), from, to).size());
    }

    @Test
    void reminderIsUniquePerBookingAndScheduled() {
        Resource resource = resource();
        User owner = user(department("Кафедра ИВТ"), UserRole.EMPLOYEE);
        Instant startsAt = Instant.parse("2027-03-01T09:00:00Z");
        Booking booking = booking(resource, owner, startsAt);

        Reminder reminder = new Reminder();
        reminder.setBooking(booking);
        reminder.setRemindAt(startsAt.minus(30, ChronoUnit.MINUTES));
        reminderRepository.saveAndFlush(reminder);

        Reminder loaded = reminderRepository.findByBookingId(booking.getId()).orElseThrow();
        assertEquals(ReminderStatus.PENDING, loaded.getStatus());
        assertEquals("log", loaded.getChannel());
        assertEquals(0, loaded.getAttempts());
        assertTrue(reminderRepository.existsByBookingId(booking.getId()));

        Reminder second = new Reminder();
        second.setBooking(booking);
        second.setRemindAt(startsAt.minus(10, ChronoUnit.MINUTES));
        assertThrows(DataIntegrityViolationException.class, () -> reminderRepository.saveAndFlush(second));

        assertTrue(reminderRepository.findDue(ReminderStatus.PENDING, Instant.now().plus(400, ChronoUnit.DAYS),
                PageRequest.of(0, 10)).stream().anyMatch(r -> r.getId().equals(loaded.getId())));

        loaded.markSent(Instant.now());
        reminderRepository.saveAndFlush(loaded);
        assertEquals(ReminderStatus.SENT, reminderRepository.findById(loaded.getId()).orElseThrow().getStatus());
        assertEquals(1, reminderRepository.findById(loaded.getId()).orElseThrow().getAttempts());
    }

    @Test
    void externalBusyIsCheckableAndRefreshable() {
        Resource resource = resource();
        User owner = user(department("Кафедра ИВТ"), UserRole.EMPLOYEE);
        Instant startsAt = Instant.parse("2027-03-01T09:00:00Z");
        booking(resource, owner, startsAt);

        ExternalBusy busy = new ExternalBusy();
        busy.setResource(resource);
        busy.setExternalId("ext-" + SEQ.incrementAndGet());
        busy.setStartsAt(startsAt.plus(3, ChronoUnit.HOURS));
        busy.setEndsAt(startsAt.plus(4, ChronoUnit.HOURS));
        externalBusyRepository.saveAndFlush(busy);

        ExternalBusy loaded = externalBusyRepository
                .findByResourceIdAndExternalId(resource.getId(), busy.getExternalId()).orElseThrow();
        assertNotNull(loaded.getFetchedAt());
        assertEquals(1, externalBusyRepository.findAllByResourceIdOrderByStartsAtAsc(resource.getId()).size());
        assertTrue(externalBusyRepository.existsOverlapping(resource.getId(),
                startsAt.plus(3, ChronoUnit.HOURS), startsAt.plus(5, ChronoUnit.HOURS)));
        assertFalse(externalBusyRepository.existsOverlapping(resource.getId(),
                startsAt, startsAt.plus(1, ChronoUnit.HOURS)));
        assertEquals(1, externalBusyRepository.findAllByFetchedAtBefore(
                Instant.now().plus(1, ChronoUnit.MINUTES)).size());
    }

    @Test
    void outboxStoresJsonbPayloadAndDetectsActiveRecord() {
        Resource resource = resource();
        User owner = user(department("Кафедра ИВТ"), UserRole.EMPLOYEE);
        Instant startsAt = Instant.parse("2027-03-01T09:00:00Z");
        Booking booking = booking(resource, owner, startsAt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("resource_code", resource.getCode());
        payload.put("starts_at", startsAt.toString());
        payload.put("weeks", 4);
        payload.put("features", List.of("проектор"));

        SyncOutbox outbox = new SyncOutbox();
        outbox.setBooking(booking);
        outbox.setOperation(OutboxOperation.CREATE);
        outbox.setPayload(payload);
        outboxRepository.saveAndFlush(outbox);

        SyncOutbox loaded = outboxRepository.findAllByBookingId(booking.getId()).getFirst();
        assertEquals(resource.getCode(), loaded.getPayload().get("resource_code"));
        assertEquals(4, loaded.getPayload().get("weeks"));
        assertEquals(List.of("проектор"), loaded.getPayload().get("features"));
        assertEquals(OutboxOperation.CREATE, loaded.getOperation());
        assertEquals(OutboxStatus.PENDING, loaded.getStatus());
        assertNotNull(loaded.getNextAttemptAt());
        assertTrue(outboxRepository.existsActiveByBookingAndOperation(booking.getId(), OutboxOperation.CREATE));

        SyncOutbox duplicate = new SyncOutbox();
        duplicate.setBooking(booking);
        duplicate.setOperation(OutboxOperation.CREATE);
        duplicate.setPayload(Map.of("resource_code", resource.getCode()));
        assertThrows(DataIntegrityViolationException.class, () -> outboxRepository.saveAndFlush(duplicate));

        assertTrue(outboxRepository.findReadyToSend(OutboxStatus.PENDING, Instant.now().plusSeconds(1),
                PageRequest.of(0, 10)).stream().anyMatch(o -> o.getId().equals(loaded.getId())));

        loaded.markInFlight();
        outboxRepository.saveAndFlush(loaded);
        assertEquals(OutboxStatus.IN_FLIGHT, outboxRepository.findById(loaded.getId()).orElseThrow().getStatus());

        loaded.scheduleRetry("calendar 503");
        outboxRepository.saveAndFlush(loaded);
        SyncOutbox retried = outboxRepository.findById(loaded.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, retried.getStatus());
        assertEquals(1, retried.getAttempts());
        assertEquals("calendar 503", retried.getLastError());
        assertTrue(retried.getNextAttemptAt().isAfter(Instant.now()), "backoff откладывает следующую попытку");

        retried.markSent();
        outboxRepository.saveAndFlush(retried);
        assertEquals(OutboxStatus.SENT, outboxRepository.findById(retried.getId()).orElseThrow().getStatus());
        assertFalse(outboxRepository.existsActiveByBookingAndOperation(booking.getId(), OutboxOperation.CREATE));
        assertFalse(outboxRepository.findReadyToSend(OutboxStatus.PENDING, Instant.now().plusSeconds(1),
                PageRequest.of(0, 10)).stream().anyMatch(o -> o.getId().equals(retried.getId())));
    }

    @Test
    void auditLogStoresJsonbAndInet() throws Exception {
        User actor = user(department("Кафедра ИВТ"), UserRole.ADMIN);

        AuditLog audit = new AuditLog();
        audit.setActor(actor);
        audit.setAction("booking.create");
        audit.setObjectType("booking");
        audit.setObjectId("4217");
        audit.setAfterState(Map.of("status", "confirmed", "purpose", "Совещание"));
        audit.setRequestId("req-" + SEQ.incrementAndGet());
        audit.setIp(InetAddress.getByName("10.0.0.5"));
        auditLogRepository.saveAndFlush(audit);

        AuditLog loaded = auditLogRepository
                .findAllByObjectTypeAndObjectIdOrderByCreatedAtDesc("booking", "4217").getFirst();
        assertEquals("10.0.0.5", loaded.getIp().getHostAddress());
        assertEquals("confirmed", loaded.getAfterState().get("status"));
        assertNull(loaded.getBeforeState());
        assertEquals(1, auditLogRepository.countByAction("booking.create"));
        assertNotNull(auditLogRepository.findAllByActorIdOrderByCreatedAtDesc(actor.getId()).getFirst());
        assertFalse(auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)).isEmpty());
    }

    @Test
    void loginAttemptsRateLimitWindow() throws Exception {
        String email = "Locked" + SEQ.incrementAndGet() + "@Example.RU";
        InetAddress ip = InetAddress.getByName("192.168.1.10");

        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(email.toLowerCase());
        attempt.setIp(ip);
        loginAttemptRepository.saveAndFlush(attempt);

        assertEquals(1, loginAttemptRepository.countByEmailAndIpAndCreatedAtAfter(
                email.toUpperCase(), ip, Instant.now().minus(10, ChronoUnit.MINUTES)));
        assertEquals(0, loginAttemptRepository.countByEmailAndIpAndCreatedAtAfter(
                email, ip, Instant.now().plus(1, ChronoUnit.MINUTES)));
        assertFalse(loginAttemptRepository.findAllByEmailOrderByCreatedAtDesc(email, PageRequest.of(0, 5)).isEmpty());

        assertEquals(1, loginAttemptRepository.deleteOlderThan(Instant.now().plus(1, ChronoUnit.MINUTES)));
        assertEquals(0, loginAttemptRepository.findAllByEmailOrderByCreatedAtDesc(email, Pageable.unpaged()).size());
    }

    @Test
    void pessimisticLockQueriesWorkInsideTransaction() {
        Department department = department("Кафедра ИВТ");
        User owner = user(department, UserRole.EMPLOYEE);
        Resource resource = resource();
        Booking booking = booking(resource, owner, Instant.parse("2027-03-01T09:00:00Z"));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long lockedUserId = tx.execute(status ->
                userRepository.findByIdForUpdate(owner.getId()).orElseThrow().getId());
        assertEquals(owner.getId(), lockedUserId);

        Long lockedBookingId = tx.execute(status ->
                bookingRepository.findByIdForUpdate(booking.getId()).orElseThrow().getId());
        assertEquals(booking.getId(), lockedBookingId);
    }

    private static List<String> codesOf(List<Resource> resources) {
        return resources.stream().map(Resource::getCode).toList();
    }
}
