package io.casehub.work.api;

/**
 * A named completion classification declared on a WorkItemTemplate.
 *
 * <p>
 * Outcomes give templates a machine-readable set of valid result states
 * (e.g. {@code approved}, {@code rejected}, {@code needs-revision}). The engine
 * can switch on {@code outcome} in {@code outputMapping} without parsing the
 * free-form {@code resolution} field. Aligned with the Open Human Task (OHT) spec.
 *
 * @param name         Machine-readable key — lowercase, URL-safe (e.g. {@code needs-revision}).
 * @param displayName  Human-readable label resolved via template lookup; null when not set.
 */
public record Outcome(String name, String displayName) {}
