package io.casehub.work.core.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerSelectionStrategy;

/**
 * Distributes WorkItems sequentially across candidates in round-robin order.
 *
 * <p>
 * The cursor position is persisted per candidate pool (keyed by a SHA-256 hash of the
 * sorted candidate IDs). Cluster-safe via {@link RoutingCursorStore} which uses OCC.
 *
 * <p>
 * Activated by: {@code casehub.work.routing.strategy=round-robin}.
 */
public class RoundRobinStrategy implements WorkerSelectionStrategy {

    @Override
    public String id() { return "round-robin"; }

    private final RoutingCursorStore cursorStore;

    public RoundRobinStrategy(final RoutingCursorStore cursorStore) {
        this.cursorStore = cursorStore;
    }

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        if (candidates.isEmpty()) {
            return AssignmentDecision.noChange();
        }
        final String poolHash = hashPool(candidates);
        final int index = cursorStore.acquireNext(poolHash, candidates.size());
        return AssignmentDecision.assignTo(candidates.get(index).id());
    }

    static String hashPool(final List<WorkerCandidate> candidates) {
        final String sorted = candidates.stream()
                .map(WorkerCandidate::id)
                .sorted()
                .collect(Collectors.joining(","));
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(sorted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
