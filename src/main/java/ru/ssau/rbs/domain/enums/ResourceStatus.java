package ru.ssau.rbs.domain.enums;

public enum ResourceStatus {

    ACTIVE("active"),
    ARCHIVED("archived");

    private final String code;

    ResourceStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ResourceStatus fromCode(String code) {
        for (ResourceStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус ресурса: " + code);
    }
}
