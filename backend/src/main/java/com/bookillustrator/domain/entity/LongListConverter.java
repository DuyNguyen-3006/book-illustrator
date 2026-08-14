package com.bookillustrator.domain.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Stores a {@code List<Long>} as a comma-separated TEXT column — see {@link Chapter#characterIds}. */
@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {

    @Override
    public String convertToDatabaseColumn(List<Long> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public List<Long> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Stream.of(dbData.split(",")).map(Long::valueOf).toList();
    }
}
