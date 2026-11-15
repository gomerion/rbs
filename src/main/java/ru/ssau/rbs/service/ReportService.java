package ru.ssau.rbs.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.ssau.rbs.domain.enums.UserRole;
import ru.ssau.rbs.dto.report.UtilizationItem;
import ru.ssau.rbs.dto.report.UtilizationPeriod;
import ru.ssau.rbs.dto.report.UtilizationResponse;
import ru.ssau.rbs.exception.ApiException;
import ru.ssau.rbs.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String ACTIVE_STATUSES = "'confirmed','moved'";
    private static final int TOP_RESOURCES = 5;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public UtilizationResponse utilization(Instant from, Instant to, String groupBy, UserPrincipal actor) {
        validatePeriod(from, to);
        String grouping = normalizeGroupBy(groupBy);
        Long departmentId = departmentScope(actor);

        Map<Long, String> codeById = new HashMap<>();
        Map<Long, String> typeById = new HashMap<>();
        Map<Long, ZoneId> zoneById = new HashMap<>();
        jdbcTemplate.query("select id, code, type, timezone from resources", rs -> {
            Long id = rs.getLong("id");
            codeById.put(id, rs.getString("code"));
            typeById.put(id, rs.getString("type"));
            zoneById.put(id, ZoneId.of(rs.getString("timezone")));
        });

        Map<Long, Double> hoursByResource = workingHoursByResource(from, to, zoneById);
        Map<String, Double> hoursByCode = new HashMap<>();
        Map<String, Double> hoursByType = new HashMap<>();
        hoursByResource.forEach((id, hours) -> {
            hoursByCode.merge(codeById.get(id), hours, Double::sum);
            hoursByType.merge(typeById.get(id), hours, Double::sum);
        });
        Map<String, Double> hoursByDepartment = hoursByDepartment(from, to, departmentId, hoursByResource);

        List<UtilizationItem> items = groupRows(from, to, grouping, departmentId).stream()
                .map(row -> {
                    String key = row.key();
                    double hours = switch (grouping) {
                        case "resource" -> hoursByCode.getOrDefault(key, 0.0);
                        case "type" -> hoursByType.getOrDefault(key, 0.0);
                        default -> hoursByDepartment.getOrDefault(key, 0.0);
                    };
                    return new UtilizationItem(key, row.bookings(), round(row.busyHours()), ratio(row.busyHours(), hours));
                })
                .toList();

        List<UtilizationItem> top = topResources(from, to, departmentId).stream()
                .map(row -> new UtilizationItem(row.key(), row.bookings(), round(row.busyHours()),
                        ratio(row.busyHours(), hoursByCode.getOrDefault(row.key(), 0.0))))
                .toList();

        return new UtilizationResponse(new UtilizationPeriod(from, to), grouping, items, top);
    }

    public String toCsv(UtilizationResponse response) {
        StringBuilder csv = new StringBuilder("key,bookings,busy_hours,utilization_ratio\n");
        for (UtilizationItem item : response.getItems()) {
            csv.append(escape(item.getKey())).append(',')
                    .append(item.getBookings()).append(',')
                    .append(item.getBusyHours()).append(',')
                    .append(item.getUtilizationRatio()).append('\n');
        }
        return csv.toString();
    }

    private List<GroupRow> groupRows(Instant from, Instant to, String grouping, Long departmentId) {
        String keyExpression = switch (grouping) {
            case "resource" -> "r.code";
            case "type" -> "r.type";
            default -> "coalesce(d.name, 'без подразделения')";
        };
        StringBuilder sql = new StringBuilder("select ").append(keyExpression)
                .append(" as key, count(*) as bookings, ")
                .append("coalesce(sum(extract(epoch from (least(b.ends_at, ?) - greatest(b.starts_at, ?))) / 3600.0), 0) as busy_hours ")
                .append("from bookings b join resources r on r.id = b.resource_id join users u on u.id = b.user_id ")
                .append("left join departments d on d.id = u.department_id ")
                .append("where b.status in (").append(ACTIVE_STATUSES).append(") ")
                .append("and b.starts_at < ? and b.ends_at > ?");
        List<Object> args = new ArrayList<>(List.of(timestamp(to), timestamp(from), timestamp(to), timestamp(from)));
        if (departmentId != null) {
            sql.append(" and u.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" group by 1 order by busy_hours desc, key");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                new GroupRow(rs.getString("key"), rs.getLong("bookings"), rs.getDouble("busy_hours")), args.toArray());
    }

    private List<GroupRow> topResources(Instant from, Instant to, Long departmentId) {
        StringBuilder sql = new StringBuilder("select r.code as key, count(*) as bookings, ")
                .append("coalesce(sum(extract(epoch from (least(b.ends_at, ?) - greatest(b.starts_at, ?))) / 3600.0), 0) as busy_hours ")
                .append("from bookings b join resources r on r.id = b.resource_id join users u on u.id = b.user_id ")
                .append("where b.status in (").append(ACTIVE_STATUSES).append(") ")
                .append("and b.starts_at < ? and b.ends_at > ?");
        List<Object> args = new ArrayList<>(List.of(timestamp(to), timestamp(from), timestamp(to), timestamp(from)));
        if (departmentId != null) {
            sql.append(" and u.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" group by r.code order by busy_hours desc limit ").append(TOP_RESOURCES);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                new GroupRow(rs.getString("key"), rs.getLong("bookings"), rs.getDouble("busy_hours")), args.toArray());
    }

    private Map<Long, Double> workingHoursByResource(Instant from, Instant to, Map<Long, ZoneId> zoneById) {
        Map<Long, Double> hours = new HashMap<>();
        jdbcTemplate.query("select resource_id, weekday, start_time, end_time from resource_working_hours",
                rs -> {
                    Long resourceId = rs.getLong("resource_id");
                    ZoneId zone = zoneById.get(resourceId);
                    if (zone == null) {
                        return;
                    }
                    int weekday = rs.getInt("weekday");
                    java.time.LocalTime start = rs.getObject("start_time", java.time.LocalTime.class);
                    java.time.LocalTime end = rs.getObject("end_time", java.time.LocalTime.class);
                    double value = hoursInPeriod(from, to, zone, weekday, start, end);
                    if (value > 0) {
                        hours.merge(resourceId, value, Double::sum);
                    }
                });
        return hours;
    }

    private Map<String, Double> hoursByDepartment(Instant from, Instant to, Long departmentId,
                                                  Map<Long, Double> hoursByResource) {
        StringBuilder sql = new StringBuilder("select coalesce(d.name, 'без подразделения') as department, b.resource_id ")
                .append("from bookings b join users u on u.id = b.user_id ")
                .append("left join departments d on d.id = u.department_id ")
                .append("where b.status in (").append(ACTIVE_STATUSES).append(") ")
                .append("and b.starts_at < ? and b.ends_at > ?");
        List<Object> args = new ArrayList<>(List.of(timestamp(to), timestamp(from)));
        if (departmentId != null) {
            sql.append(" and u.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" group by 1, 2");
        Map<String, Double> hours = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            String department = rs.getString("department");
            Long resourceId = rs.getLong("resource_id");
            hours.merge(department, hoursByResource.getOrDefault(resourceId, 0.0), Double::sum);
        }, args.toArray());
        return hours;
    }

    private static double hoursInPeriod(Instant from, Instant to, ZoneId zone, int weekday,
                                        java.time.LocalTime start, java.time.LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return 0;
        }
        double hours = 0;
        LocalDate firstDay = from.atZone(zone).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(zone).toLocalDate();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() != weekday) {
                continue;
            }
            long windowStart = day.atTime(start).atZone(zone).toEpochSecond();
            long windowEnd = day.atTime(end).atZone(zone).toEpochSecond();
            long overlap = Math.min(windowEnd, to.getEpochSecond()) - Math.max(windowStart, from.getEpochSecond());
            if (overlap > 0) {
                hours += overlap / 3600.0;
            }
        }
        return hours;
    }

    private static Long departmentScope(UserPrincipal actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            return null;
        }
        if (actor.getRole() != UserRole.MANAGER) {
            throw ApiException.forbidden("Отчёт доступен руководителю подразделения и администратору");
        }
        if (actor.getDepartmentId() == null) {
            throw ApiException.forbidden("У руководителя не задано подразделение");
        }
        return actor.getDepartmentId();
    }

    private static String normalizeGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "resource";
        }
        String value = groupBy.trim().toLowerCase(Locale.ROOT);
        if (!List.of("resource", "type", "department").contains(value)) {
            throw ApiException.validationError("group_by: допустимы resource, type, department");
        }
        return value;
    }

    private static void validatePeriod(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.validationError("from и to обязательны");
        }
        if (!from.isBefore(to)) {
            throw ApiException.validationError("from: должен быть раньше to");
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double ratio(double busyHours, double workingHours) {
        return workingHours <= 0 ? 0 : round(busyHours / workingHours);
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private record GroupRow(String key, long bookings, double busyHours) {
    }
}
