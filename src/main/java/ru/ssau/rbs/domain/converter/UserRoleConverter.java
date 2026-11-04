package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.UserRole;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.fromCode(dbData);
    }
}
