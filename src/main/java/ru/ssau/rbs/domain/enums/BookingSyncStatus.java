package ru.ssau.rbs.domain.enums;

public enum BookingSyncStatus {

    PENDING("pending"),
    SYNCED("synced"),
    FAILED("failed");

    private final String code;

    BookingSyncStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static BookingSyncStatus fromCode(String code) {
        for (BookingSyncStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус синхронизации брони: " + code);
    }
}
