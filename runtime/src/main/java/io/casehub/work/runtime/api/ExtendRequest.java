package io.casehub.work.runtime.api;

import java.time.Instant;

public record ExtendRequest(Instant newExpiresAt) {
}
