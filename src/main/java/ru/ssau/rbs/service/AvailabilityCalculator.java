package ru.ssau.rbs.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.entity.Resource;
import ru.ssau.rbs.domain.entity.ResourceWorkingHour;
import ru.ssau.rbs.domain.enums.BookingStatus;
import ru.ssau.rbs.dto.resource.AvailabilitySlot;
import ru.ssau.rbs.repository.BookingRepository;
import ru.ssau.rbs.repository.ExternalBusyRepository;
import ru.ssau.rbs.repository.ResourceWorkingHourRepository;

@Service
@RequiredArgsConstructor
public class AvailabilityCalculator {

    private static final int GRID_MINUTES = 15;
    private static final Duration MIN_SLOT = Duration.ofMinutes(GRID_MINUTES);

    private final ResourceWorkingHourRepository workingHourRepository;
    private final BookingRepository bookingRepository;
    private final ExternalBusyRepository externalBusyRepository;

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> slots(Resource resource, Instant from, Instant to) {
        ZoneId zone = ZoneId.of(resource.getTimeZone());
        Map<Integer, List<ResourceWorkingHour>> hoursByWeekday = workingHourRepository
                .findAllByResourceId(resource.getId()).stream()
                .collect(Collectors.groupingBy(hour -> hour.getId().getWeekday().intValue()));
        List<Interval> busy = busyIntervals(resource.getId(), from, to);
        Instant now = Instant.now();
        Instant earliest = ceilToGrid(now, zone);
        List<AvailabilitySlot> slots = new ArrayList<>();

        LocalDate firstDay = from.atZone(zone).toLocalDate();
        LocalDate lastDay = to.atZone(zone).toLocalDate();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            for (ResourceWorkingHour hour : hoursByWeekday.getOrDefault(day.getDayOfWeek().getValue(), List.of())) {
                Instant windowStart = day.atTime(hour.getId().getStartTime()).atZone(zone).toInstant();
                Instant windowEnd = day.atTime(hour.getEndTime()).atZone(zone).toInstant();
                Instant lower = later(windowStart, from);
                Instant upper = earlier(windowEnd, to);
                if (!lower.isBefore(upper)) {
                    continue;
                }
                for (Interval free : subtract(lower, upper, busy)) {
                    Instant start = later(ceilToGrid(free.start(), zone), earliest);
                    Instant end = floorToGrid(free.end(), zone);
                    if (Duration.between(start, end).compareTo(MIN_SLOT) >= 0) {
                        slots.add(new AvailabilitySlot(start, end));
                    }
                }
            }
        }
        return merge(slots);
    }

    private List<Interval> busyIntervals(Long resourceId, Instant from, Instant to) {
        List<Interval> intervals = new ArrayList<>();
        for (var booking : bookingRepository.findByResourceAndPeriod(resourceId, BookingStatus.ACTIVE, from, to)) {
            intervals.add(new Interval(booking.getStartsAt(), booking.getEndsAt()));
        }
        for (var external : externalBusyRepository.findOverlapping(resourceId, from, to)) {
            intervals.add(new Interval(external.getStartsAt(), external.getEndsAt()));
        }
        return intervals.stream().sorted(Comparator.comparing(Interval::start)).toList();
    }

    private static List<Interval> subtract(Instant lower, Instant upper, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        Instant cursor = lower;
        for (Interval interval : busy) {
            if (!interval.end().isAfter(cursor)) {
                continue;
            }
            if (!interval.start().isBefore(upper)) {
                break;
            }
            if (interval.start().isAfter(cursor)) {
                free.add(new Interval(cursor, earlier(interval.start(), upper)));
            }
            cursor = later(cursor, interval.end());
            if (!cursor.isBefore(upper)) {
                break;
            }
        }
        if (cursor.isBefore(upper)) {
            free.add(new Interval(cursor, upper));
        }
        return free;
    }

    private static List<AvailabilitySlot> merge(List<AvailabilitySlot> slots) {
        List<AvailabilitySlot> merged = new ArrayList<>();
        for (AvailabilitySlot slot : slots.stream().sorted(Comparator.comparing(AvailabilitySlot::getStart)).toList()) {
            if (!merged.isEmpty() && !slot.getStart().isAfter(merged.getLast().getEnd())) {
                AvailabilitySlot last = merged.getLast();
                if (slot.getEnd().isAfter(last.getEnd())) {
                    merged.set(merged.size() - 1, new AvailabilitySlot(last.getStart(), slot.getEnd()));
                }
            } else {
                merged.add(slot);
            }
        }
        return merged;
    }

    private static Instant ceilToGrid(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        int remainder = local.getMinute() % GRID_MINUTES;
        ZonedDateTime truncated = local.withSecond(0).withNano(0).withMinute(local.getMinute() - remainder);
        if (remainder == 0 && local.getSecond() == 0 && local.getNano() == 0) {
            return truncated.toInstant();
        }
        return truncated.plusMinutes(GRID_MINUTES).toInstant();
    }

    private static Instant floorToGrid(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        return local.withSecond(0).withNano(0)
                .withMinute(local.getMinute() - local.getMinute() % GRID_MINUTES)
                .toInstant();
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private record Interval(Instant start, Instant end) {
    }
}
