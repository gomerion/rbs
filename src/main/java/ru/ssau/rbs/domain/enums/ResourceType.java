package ru.ssau.rbs.domain.enums;

public enum ResourceType {

    ROOM("room"),
    EQUIPMENT("equipment"),
    VEHICLE("vehicle");

    private final String code;

    ResourceType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ResourceType fromCode(String code) {
        for (ResourceType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип ресурса: " + code);
    }
}
