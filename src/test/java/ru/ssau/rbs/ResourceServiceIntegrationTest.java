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
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.ResourceStatus;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.common.PageResponse;
import ru.ssau.rbs.dto.resource.AvailabilityResponse;
import ru.ssau.rbs.dto.resource.AvailabilitySlot;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourcePatchRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.dto.resource.WorkingHoursDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.DepartmentRepository;
import ru.ssau.rbs.repository.ExternalBusyRepository;
import ru.ssau.rbs.repository.ResourceRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.service.ResourceService;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ResourceServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private static final ZoneId SAMARA = ZoneId.of("Europe/Samara");

    @Autowired
    ResourceService resourceService;
    @Autowired
    ResourceRepository resourceRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    ExternalBusyRepository externalBusyRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DepartmentRepository departmentRepository;

    private static List<WorkingHoursDto> wholeWeek() {
        List<WorkingHoursDto> hours = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            hours.add(new WorkingHoursDto(weekday, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        }
        return hours;
    }

    private static ResourceCreateRequest request(String code, String type, int capacity, String location,
                                                 List<String> features) {
        return new ResourceCreateRequest(code, "Ресурс " + code, type, capacity, location, features,
                "Europe/Samara", wholeWeek(), "cal-" + code);
    }

    private String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    @Test
    void createReturnsResourceWithWorkingHours() {
        String code = unique("room");
        ResourceResponse response = resourceService.create(request(code, "room", 12, "Корпус 3", List.of("проектор")));

        assertEquals(code, response.getCode());
        assertEquals("room", response.getType());
        assertEquals(12, response.getCapacity());
        assertEquals("active", response.getStatus());
        assertEquals("Europe/Samara", response.getTimeZone());
        assertEquals(List.of("проектор"), response.getFeatures());
        assertEquals(7, response.getWorkingHours().size());
        assertEquals(LocalTime.of(8, 0), response.getWorkingHours().getFirst().getStartTime());

        assertEquals(response.getId(), resourceService.get(response.getId()).getId());
    }

    @Test
    void createRejectsDuplicateCode() {
        String code = unique("room");
        resourceService.create(request(code, "room", 4, "Корпус 1", List.of()));

        ApiException exception = assertThrows(ApiException.class,
                () -> resourceService.create(request(code, "room", 4, "Корпус 2", List.of())));

        assertEquals("conflict", exception.getCode());
        assertEquals(409, exception.getStatus().value());
    }

    @Test
    void createRejectsInvalidValues() {
        assertEquals("validation_error", assertThrows(ApiException.class, () -> resourceService.create(
                new ResourceCreateRequest(unique("x"), "Имя", "table", 1, "Корпус", List.of(),
                        "Europe/Samara", wholeWeek(), null))).getCode());

        assertEquals("validation_error", assertThrows(ApiException.class, () -> resourceService.create(
                new ResourceCreateRequest(unique("x"), "Имя", "room", 1, "Корпус", List.of(),
                        "Mars/Olympus", wholeWeek(), null))).getCode());

        assertEquals("validation_error", assertThrows(ApiException.class, () -> resourceService.create(
                new ResourceCreateRequest(unique("x"), "Имя", "room", 1, "Корпус", List.of(),
                        "Europe/Samara", List.of(new WorkingHoursDto(1, LocalTime.of(20, 0), LocalTime.of(8, 0))),
                        null))).getCode());
    }

    @Test
    void getReturnsNotFoundForUnknownResource() {
        ApiException exception = assertThrows(ApiException.class, () -> resourceService.get(999_999_999L));

        assertEquals("not_found", exception.getCode());
        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void searchFiltersByLocationCapacityAndType() {
        String location = unique("Корпус");
        resourceService.create(request(unique("room"), "room", 20, location, List.of()));
        resourceService.create(request(unique("equipment"), "equipment", 2, location, List.of()));

        PageResponse<ResourceResponse> rooms = resourceService.search("room", 5, null, location, null, "name", 100, 0);
        assertEquals(1, rooms.getTotal());
        assertEquals("room", rooms.getItems().getFirst().getType());

        PageResponse<ResourceResponse> all = resourceService.search(null, null, null, location, null, "name", 100, 0);
        assertEquals(2, all.getTotal());

        PageResponse<ResourceResponse> big = resourceService.search(null, 10, null, location, null, "name", 100, 0);
        assertEquals(1, big.getTotal());
    }

    @Test
    void searchRequiresAllFeatures() {
        String location = unique("Корпус");
        resourceService.create(request(unique("room"), "room", 6, location, List.of("проектор", "кондиционер")));
        resourceService.create(request(unique("room"), "room", 6, location, List.of("проектор")));

        PageResponse<ResourceResponse> both = resourceService.search(null, null,
                List.of("проектор", "кондиционер"), location, null, "name", 100, 0);
        assertEquals(1, both.getTotal());

        PageResponse<ResourceResponse> single = resourceService.search(null, null,
                List.of("проектор"), location, null, "name", 100, 0);
        assertEquals(2, single.getTotal());

        PageResponse<ResourceResponse> missing = resourceService.search(null, null,
                List.of("парковка"), location, null, "name", 100, 0);
        assertEquals(0, missing.getTotal());
        assertTrue(missing.getItems().isEmpty());
    }

    @Test
    void searchPaginatesAndValidatesLimit() {
        String location = unique("Корпус");
        for (int i = 0; i < 3; i++) {
            resourceService.create(request(unique("room"), "room", 3, location, List.of()));
        }

        PageResponse<ResourceResponse> first = resourceService.search(null, null, null, location, null, "name", 2, 0);
        assertEquals(3, first.getTotal());
        assertEquals(2, first.getItems().size());
        assertEquals(2, first.getLimit());
        assertEquals(0, first.getOffset());

        PageResponse<ResourceResponse> rest = resourceService.search(null, null, null, location, null, "name", 2, 2);
        assertEquals(3, rest.getTotal());
        assertEquals(1, rest.getItems().size());

        PageResponse<ResourceResponse> shifted = resourceService.search(null, null, null, location, null, "name", 1, 1);
        assertEquals(1, shifted.getItems().size());
        assertEquals(first.getItems().get(1).getCode(), shifted.getItems().getFirst().getCode());

        assertEquals("validation_error", assertThrows(ApiException.class,
                () -> resourceService.search(null, null, null, location, null, "name", 101, 0)).getCode());
        assertEquals("validation_error", assertThrows(ApiException.class,
                () -> resourceService.search(null, null, null, location, null, "name", 10, -1)).getCode());
    }

    @Test
    void archiveHidesResourceFromDefaultSearch() {
        String code = unique("room");
        ResourceResponse created = resourceService.create(request(code, "room", 3, unique("Корпус"), List.of()));

        ResourceResponse archived = resourceService.archive(created.getId());
        assertEquals("archived", archived.getStatus());

        PageResponse<ResourceResponse> active = resourceService.search(null, null, null, null, null, "name", 100, 0);
        assertFalse(active.getItems().stream().anyMatch(item -> item.getId().equals(created.getId())));

        PageResponse<ResourceResponse> archivedOnly = resourceService.search(null, null, null, null, "archived", "name", 100, 0);
        assertTrue(archivedOnly.getItems().stream().anyMatch(item -> item.getId().equals(created.getId())));

        assertEquals("archived", resourceService.get(created.getId()).getStatus());
    }

    @Test
    void updateReplacesFieldsAndWorkingHours() {
        ResourceResponse created = resourceService.create(
                request(unique("room"), "room", 3, unique("Корпус"), List.of("проектор")));

        ResourceResponse updated = resourceService.update(created.getId(), new ResourcePatchRequest(null,
                "Новое имя", null, 8, "Корпус 7", List.of("доска"), "Europe/Moscow",
                List.of(new WorkingHoursDto(1, LocalTime.of(9, 0), LocalTime.of(18, 0))), null, null));

        assertEquals("Новое имя", updated.getName());
        assertEquals(8, updated.getCapacity());
        assertEquals("Корпус 7", updated.getLocation());
        assertEquals(List.of("доска"), updated.getFeatures());
        assertEquals("Europe/Moscow", updated.getTimeZone());
        assertEquals(1, updated.getWorkingHours().size());
        assertEquals(LocalTime.of(9, 0), updated.getWorkingHours().getFirst().getStartTime());

        ResourceResponse fromDatabase = resourceService.get(created.getId());
        assertEquals("Новое имя", fromDatabase.getName());
        assertEquals(1, fromDatabase.getWorkingHours().size());
    }

    @Test
    void availabilityExcludesBookingsExternalBusyAndPast() {
        ResourceResponse resource = resourceService.create(
                request(unique("room"), "room", 6, unique("Корпус"), List.of()));

        LocalDate day = LocalDate.now(SAMARA).plusDays(3);
        Instant bookingStart = day.atTime(10, 0).atZone(SAMARA).toInstant();
        Instant externalStart = day.atTime(14, 0).atZone(SAMARA).toInstant();
        User user = user();
        booking(resource.getId(), user, bookingStart, bookingStart.plusSeconds(3600));
        externalBusy(resource.getId(), externalStart, externalStart.plusSeconds(3600));

        Instant from = day.atTime(8, 0).atZone(SAMARA).toInstant();
        Instant to = day.atTime(20, 0).atZone(SAMARA).toInstant();
        AvailabilityResponse response = resourceService.availability(resource.getId(), from, to);

        List<AvailabilitySlot> slots = response.getItems();
        assertEquals(3, slots.size());
        assertEquals(local(slots.get(0).getStart()), LocalTime.of(8, 0));
        assertEquals(local(slots.get(0).getEnd()), LocalTime.of(10, 0));
        assertEquals(local(slots.get(1).getStart()), LocalTime.of(11, 0));
        assertEquals(local(slots.get(1).getEnd()), LocalTime.of(14, 0));
        assertEquals(local(slots.get(2).getStart()), LocalTime.of(15, 0));
        assertEquals(local(slots.get(2).getEnd()), LocalTime.of(20, 0));
    }

    @Test
    void availabilityAlignsWindowToGrid() {
        ResourceResponse resource = resourceService.create(
                request(unique("room"), "room", 6, unique("Корпус"), List.of()));

        LocalDate day = LocalDate.now(SAMARA).plusDays(4);
        Instant from = day.atTime(9, 37).atZone(SAMARA).toInstant();
        Instant to = day.atTime(12, 7).atZone(SAMARA).toInstant();
        AvailabilityResponse response = resourceService.availability(resource.getId(), from, to);

        assertEquals(1, response.getItems().size());
        assertEquals(local(response.getItems().getFirst().getStart()), LocalTime.of(9, 45));
        assertEquals(local(response.getItems().getFirst().getEnd()), LocalTime.of(12, 0));
    }

    @Test
    void availabilityRejectsInvertedPeriod() {
        ResourceResponse resource = resourceService.create(
                request(unique("room"), "room", 6, unique("Корпус"), List.of()));

        Instant from = Instant.parse("2027-01-11T10:00:00Z");
        assertThrows(ApiException.class, () -> resourceService.availability(resource.getId(), from, from));
    }

    private static LocalTime local(Instant instant) {
        return instant.atZone(SAMARA).toLocalTime();
    }

    private User user() {
        Department department = new Department();
        department.setName("Кафедра-" + SEQ.incrementAndGet());
        departmentRepository.saveAndFlush(department);

        User user = new User();
        user.setEmail("user" + SEQ.incrementAndGet() + "@example.ru");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$hash");
        user.setRole(UserRole.EMPLOYEE);
        user.setDepartment(department);
        return userRepository.saveAndFlush(user);
    }

    private void booking(Long resourceId, User user, Instant startsAt, Instant endsAt) {
        Booking booking = new Booking();
        booking.setResource(resourceRepository.getReferenceById(resourceId));
        booking.setUser(user);
        booking.setCreatedBy(user);
        booking.setStartsAt(startsAt);
        booking.setEndsAt(endsAt);
        booking.setPurpose("Совещание");
        bookingRepository.saveAndFlush(booking);
    }

    private void externalBusy(Long resourceId, Instant startsAt, Instant endsAt) {
        ExternalBusy busy = new ExternalBusy();
        busy.setResource(resourceRepository.getReferenceById(resourceId));
        busy.setExternalId("ext-" + SEQ.incrementAndGet());
        busy.setStartsAt(startsAt);
        busy.setEndsAt(endsAt);
        externalBusyRepository.saveAndFlush(busy);
    }
}
