package ru.ssau.rbs.domain.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {

    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    MOVED("moved"),
    COMPLETED("completed");

    public static final Set<BookingStatus> ACTIVE =
            Collections.unmodifiableSet(EnumSet.of(CONFIRMED, MOVED));

    private final String code;

    BookingStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return this == CONFIRMED || this == MOVED;
    }

    public static BookingStatus fromCode(String code) {
        for (BookingStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус брони: " + code);
    }
}
