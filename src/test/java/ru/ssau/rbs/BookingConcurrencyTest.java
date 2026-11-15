package ru.ssau.rbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.ssau.rbs.domain.entity.Department;
import ru.ssau.rbs.domain.entity.User;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.booking.BookingCreateRequest;
import ru.ssau.rbs.dto.resource.ResourceCreateRequest;
import ru.ssau.rbs.dto.resource.ResourceResponse;
import ru.ssau.rbs.dto.resource.WorkingHoursDto;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.DepartmentRepository;
import ru.ssau.rbs.repository.UserRepository;
import ru.ssau.rbs.security.UserPrincipal;
import ru.ssau.rbs.service.BookingService;
import ru.ssau.rbs.service.ResourceService;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BookingConcurrencyTest {

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
    BookingRepository bookingRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DepartmentRepository departmentRepository;

    @Test
    void twoParallelRequestsGiveSingleBooking() throws Exception {
        Outcome outcome = runParallely(2);
        assertEquals(1, outcome.created());
        assertEquals(1, outcome.conflicted());
        assertEquals(1, outcome.storedBookings());
    }

    @Test
    void fiftyParallelRequestsGiveSingleBooking() throws Exception {
        Outcome outcome = runParallely(50);
        assertEquals(1, outcome.created());
        assertEquals(49, outcome.conflicted());
        assertEquals(1, outcome.storedBookings());
    }

    private Outcome runParallely(int attempts) throws Exception {
        ResourceResponse resource = resource();
        User admin = user(UserRole.ADMIN);
        UserPrincipal actor = new UserPrincipal(admin.getId(), admin.getEmail(), admin.getPasswordHash(),
                admin.getRole(), null);
        LocalDate day = LocalDate.now(SAMARA).plusDays(5);
        Instant startsAt = day.atTime(10, 0).atZone(SAMARA).toInstant();
        Instant endsAt = day.atTime(11, 0).atZone(SAMARA).toInstant();
        BookingCreateRequest request = new BookingCreateRequest(resource.getId(), startsAt, endsAt, "Совещание", null);

        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                ready.countDown();
                try {
                    start.await(30, TimeUnit.SECONDS);
                    bookingService.create(request, null, actor);
                    created.incrementAndGet();
                } catch (ApiException ex) {
                    if ("conflict".equals(ex.getCode())) {
                        conflicted.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception ex) {
                    unexpected.incrementAndGet();
                }
            });
        }
        tasks.forEach(pool::submit);
        assertTrue(ready.await(30, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS));
        assertEquals(0, unexpected.get());

        int stored = bookingRepository.findOverlapping(resource.getId(), BookingStatus.ACTIVE, startsAt, endsAt).size();
        return new Outcome(created.get(), conflicted.get(), stored);
    }

    private ResourceResponse resource() {
        String code = "conc-" + SEQ.incrementAndGet();
        List<WorkingHoursDto> hours = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            hours.add(new WorkingHoursDto(weekday, LocalTime.of(8, 0), LocalTime.of(20, 0)));
        }
        return resourceService.create(new ResourceCreateRequest(code, "Ресурс " + code, "room", 4, "Корпус",
                List.of(), "Europe/Samara", hours, null));
    }

    private User user(UserRole role) {
        Department department = new Department();
        department.setName("Кафедра-" + SEQ.incrementAndGet());
        departmentRepository.saveAndFlush(department);

        User user = new User();
        user.setEmail("conc" + SEQ.incrementAndGet() + "@example.ru");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$hash");
        user.setRole(role);
        user.setDepartment(department);
        return userRepository.saveAndFlush(user);
    }

    private record Outcome(int created, int conflicted, int storedBookings) {
    }
}
