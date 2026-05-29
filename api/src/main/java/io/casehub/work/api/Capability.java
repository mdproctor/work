package io.casehub.work.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated capability name. Format: lowercase kebab-case only ([a-z][a-z0-9]*(-[a-z0-9]+)*).
 * Construction fails fast with {@link MalformedCapabilityException} on format violations.
 */
public record Capability(String id) {

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    public Capability {
        Objects.requireNonNull(id);
        if (!KEBAB_CASE.matcher(id).matches()) {
            throw new MalformedCapabilityException(id);
        }
    }

    public static Capability of(String id) {
        return new Capability(id);
    }
}
