package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.BookingSyncStatus;

@Converter(autoApply = true)
public class BookingSyncStatusConverter implements AttributeConverter<BookingSyncStatus, String> {

    @Override
    public String convertToDatabaseColumn(BookingSyncStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public BookingSyncStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BookingSyncStatus.fromCode(dbData);
    }
}
