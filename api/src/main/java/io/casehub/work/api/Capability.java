package io.casehub.work.api;

import java.util.Objects;

/**
 * A validated capability name. Format: lowercase kebab-case only ([a-z][a-z0-9]*(-[a-z0-9]+)*).
 * Construction fails fast with {@link MalformedCapabilityException} on format violations.
 */
public record Capability(String id) {

    public Capability {
        Objects.requireNonNull(id);
        if (!id.matches("[a-z][a-z0-9]*(-[a-z0-9]+)*")) {
            throw new MalformedCapabilityException(id);
        }
    }

    public static Capability of(String id) {
        return new Capability(id);
    }
}
