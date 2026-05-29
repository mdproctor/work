package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

class PathAttributeConverterTest {

    private final PathAttributeConverter converter = new PathAttributeConverter();

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTrip_preservesPathValue() {
        Path original = Path.parse("legal/contracts/nda");
        String stored = converter.convertToDatabaseColumn(original);
        Path restored = converter.convertToEntityAttribute(stored);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void convertToDatabaseColumn_storesRawStringValue() {
        Path path = Path.parse("a/b/c");
        assertThat(converter.convertToDatabaseColumn(path)).isEqualTo("a/b/c");
    }
}
