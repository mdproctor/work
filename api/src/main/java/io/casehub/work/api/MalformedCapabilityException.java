package io.casehub.work.api;

import java.util.List;

public class MalformedCapabilityException extends IllegalArgumentException {

    private final List<String> badValues;

    public MalformedCapabilityException(String badValue) {
        super("Capability id must be lowercase kebab-case: " + badValue);
        this.badValues = List.of(badValue);
    }

    public List<String> badValues() {
        return badValues;
    }
}
