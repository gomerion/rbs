package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.ReminderStatus;

@Converter(autoApply = true)
public class ReminderStatusConverter implements AttributeConverter<ReminderStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReminderStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ReminderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReminderStatus.fromCode(dbData);
    }
}
