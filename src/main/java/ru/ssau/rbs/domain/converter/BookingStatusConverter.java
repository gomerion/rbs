package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.BookingStatus;

@Converter(autoApply = true)
public class BookingStatusConverter implements AttributeConverter<BookingStatus, String> {

    @Override
    public String convertToDatabaseColumn(BookingStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public BookingStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BookingStatus.fromCode(dbData);
    }
}
