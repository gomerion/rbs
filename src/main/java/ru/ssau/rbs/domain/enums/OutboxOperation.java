package ru.ssau.rbs.domain.enums;

public enum OutboxOperation {

    CREATE("create"),
    DELETE("delete");

    private final String code;

    OutboxOperation(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static OutboxOperation fromCode(String code) {
        for (OutboxOperation value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестная операция outbox: " + code);
    }
}
