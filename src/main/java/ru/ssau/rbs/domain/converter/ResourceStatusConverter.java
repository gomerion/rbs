package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.ResourceStatus;

@Converter(autoApply = true)
public class ResourceStatusConverter implements AttributeConverter<ResourceStatus, String> {

    @Override
    public String convertToDatabaseColumn(ResourceStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ResourceStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ResourceStatus.fromCode(dbData);
    }
}
