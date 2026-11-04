package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.OutboxOperation;

@Converter(autoApply = true)
public class OutboxOperationConverter implements AttributeConverter<OutboxOperation, String> {

    @Override
    public String convertToDatabaseColumn(OutboxOperation attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public OutboxOperation convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OutboxOperation.fromCode(dbData);
    }
}
