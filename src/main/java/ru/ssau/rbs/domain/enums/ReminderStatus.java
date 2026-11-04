package ru.ssau.rbs.domain.enums;

public enum ReminderStatus {

    PENDING("pending"),
    SENT("sent"),
    FAILED("failed");

    private final String code;

    ReminderStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ReminderStatus fromCode(String code) {
        for (ReminderStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус напоминания: " + code);
    }
}
