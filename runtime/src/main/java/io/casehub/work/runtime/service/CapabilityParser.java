package io.casehub.work.runtime.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.casehub.work.api.Capability;
import io.casehub.work.api.MalformedCapabilityException;

/**
 * Parses comma-separated capability strings to {@code Set<Capability>}.
 *
 * <p>Two modes: {@link #parse} (strict — throws on bad format, for new input)
 * and {@link #parseLenient} (skips bad tokens with a warning, for existing DB rows).
 */
public final class CapabilityParser {

    private static final Logger LOG = Logger.getLogger(CapabilityParser.class);

    private CapabilityParser() {}

    /**
     * Strict parse — throws {@link MalformedCapabilityException} on any malformed token.
     * Use for new user input (WorkItem creation).
     */
    public static Set<Capability> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Capability::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Lenient parse — skips malformed tokens with a WARN log entry.
     * Use when reading existing DB rows to build {@link io.casehub.work.api.SelectionContext}.
     */
    public static Set<Capability> parseLenient(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(s -> {
                    try {
                        return Stream.of(Capability.of(s));
                    } catch (MalformedCapabilityException e) {
                        LOG.warnf("Skipping malformed capability string in DB row: '%s'", s);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }
}
