package ru.ssau.rbs.domain.enums;

public enum OutboxStatus {

    PENDING("pending"),
    IN_FLIGHT("in_flight"),
    SENT("sent"),
    DEAD_LETTER("dead_letter");

    private final String code;

    OutboxStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static OutboxStatus fromCode(String code) {
        for (OutboxStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус outbox: " + code);
    }
}
