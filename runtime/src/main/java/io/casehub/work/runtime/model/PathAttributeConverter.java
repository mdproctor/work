package io.casehub.work.runtime.model;

import io.casehub.platform.api.path.Path;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// autoApply = false: Path may appear in non-entity contexts (DTOs, value objects);
// force explicit opt-in per field rather than converting every Path field globally.
@Converter(autoApply = false)
public class PathAttributeConverter implements AttributeConverter<Path, String> {

    @Override
    public String convertToDatabaseColumn(final Path path) {
        return path == null ? null : path.value();
    }

    @Override
    public Path convertToEntityAttribute(final String value) {
        return value == null ? null : Path.parse(value);
    }
}
