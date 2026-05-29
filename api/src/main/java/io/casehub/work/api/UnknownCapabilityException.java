package io.casehub.work.api;

import java.util.List;

public class UnknownCapabilityException extends RuntimeException {

    private final List<String> unknownIds;

    public UnknownCapabilityException(List<Capability> unknown) {
        super("Unknown capabilities: " + unknown.stream().map(Capability::id).toList());
        this.unknownIds = unknown.stream().map(Capability::id).toList();
    }

    /** Capability ids as Strings — safe for JSON serialisation. */
    public List<String> unknownIds() {
        return unknownIds;
    }
}
