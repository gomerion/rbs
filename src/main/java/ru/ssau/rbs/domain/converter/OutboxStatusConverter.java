package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.OutboxStatus;

@Converter(autoApply = true)
public class OutboxStatusConverter implements AttributeConverter<OutboxStatus, String> {

    @Override
    public String convertToDatabaseColumn(OutboxStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public OutboxStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OutboxStatus.fromCode(dbData);
    }
}
