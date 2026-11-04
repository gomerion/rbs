package ru.ssau.rbs.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {

    EMPLOYEE("employee"),
    MANAGER("manager"),
    ADMIN("admin");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public static UserRole fromCode(String code) {
        for (UserRole value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Неизвестная роль пользователя: " + code);
    }
}
