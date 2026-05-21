package io.casehub.work.runtime.multiinstance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;
import io.casehub.work.core.strategy.ClaimFirstStrategy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import io.casehub.work.core.strategy.RoundRobinStrategy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.model.WorkItem;

@ExtendWith(MockitoExtension.class)
class RoundRobinAssignmentStrategyTest {

    private static WorkItemsConfig configWith(final String strategy) {
        final WorkItemsConfig config = mock(WorkItemsConfig.class);
        final WorkItemsConfig.RoutingConfig routing = mock(WorkItemsConfig.RoutingConfig.class);
        when(config.routing()).thenReturn(routing);
        when(routing.strategy()).thenReturn(strategy);
        return config;
    }

    private static MultiInstanceContext context(final String candidateUsers) {
        final WorkItem parent = new WorkItem();
        parent.candidateUsers = candidateUsers;
        return new MultiInstanceContext(parent,
                new MultiInstanceConfig(1, 1, null, "roundRobin", null, false, null));
    }

    private static List<WorkItem> oneInstance() {
        final WorkItem child = new WorkItem();
        return List.of(child);
    }

    @Test
    void roundRobinConfig_delegatesToRoundRobinStrategy() {
        final ClaimFirstStrategy claimFirst = mock(ClaimFirstStrategy.class);
        final LeastLoadedStrategy leastLoaded = mock(LeastLoadedStrategy.class);
        final RoundRobinStrategy roundRobin = mock(RoundRobinStrategy.class);
        when(roundRobin.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("alice"));

        final var strategy = new RoundRobinAssignmentStrategy(
                configWith("round-robin"), claimFirst, leastLoaded, roundRobin);

        strategy.assign((List) oneInstance(), context("alice,bob"));

        verify(roundRobin).select(any(), anyList());
        verify(leastLoaded, never()).select(any(), anyList());
        verify(claimFirst, never()).select(any(), anyList());
    }

    @Test
    void claimFirstConfig_delegatesToClaimFirstStrategy() {
        final ClaimFirstStrategy claimFirst = mock(ClaimFirstStrategy.class);
        final LeastLoadedStrategy leastLoaded = mock(LeastLoadedStrategy.class);
        final RoundRobinStrategy roundRobin = mock(RoundRobinStrategy.class);
        when(claimFirst.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("bob"));

        final var strategy = new RoundRobinAssignmentStrategy(
                configWith("claim-first"), claimFirst, leastLoaded, roundRobin);

        strategy.assign((List) oneInstance(), context("alice,bob"));

        verify(claimFirst).select(any(), anyList());
        verify(leastLoaded, never()).select(any(), anyList());
        verify(roundRobin, never()).select(any(), anyList());
    }

    @Test
    void leastLoadedConfig_delegatesToLeastLoadedStrategy() {
        final ClaimFirstStrategy claimFirst = mock(ClaimFirstStrategy.class);
        final LeastLoadedStrategy leastLoaded = mock(LeastLoadedStrategy.class);
        final RoundRobinStrategy roundRobin = mock(RoundRobinStrategy.class);
        when(leastLoaded.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("carol"));

        final var strategy = new RoundRobinAssignmentStrategy(
                configWith("least-loaded"), claimFirst, leastLoaded, roundRobin);

        strategy.assign((List) oneInstance(), context("alice,bob,carol"));

        verify(leastLoaded).select(any(), anyList());
        verify(claimFirst, never()).select(any(), anyList());
        verify(roundRobin, never()).select(any(), anyList());
    }
}
