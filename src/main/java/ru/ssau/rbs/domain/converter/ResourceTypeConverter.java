package ru.ssau.rbs.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.ssau.rbs.domain.enums.ResourceType;

@Converter(autoApply = true)
public class ResourceTypeConverter implements AttributeConverter<ResourceType, String> {

    @Override
    public String convertToDatabaseColumn(ResourceType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ResourceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ResourceType.fromCode(dbData);
    }
}
