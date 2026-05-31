package io.casehub.work.examples.exclusion;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

import io.casehub.work.api.PolicyDecision;

/**
 * Demo: {@link ExpiringExclusionPolicy} policy contract.
 *
 * <p>Demonstrates all parse branches of {@link ExpiringExclusionPolicy#check} by calling
 * the policy directly with a system-clock instance. Named "Demo" to distinguish from
 * service-tier integration scenarios — audit trail enforcement via
 * {@code BlockedAttemptAuditService} is covered by {@code WorkItemExcludedUsersTest}.
 *
 * <p>Endpoint: {@code POST /examples/exclusion-policy/run}
 */
@Path("/examples/exclusion-policy")
@Produces(MediaType.APPLICATION_JSON)
public class ExclusionPolicyDemoScenario {

    private static final Logger LOG = Logger.getLogger(ExclusionPolicyDemoScenario.class);

    private static final String SCENARIO_ID = "expiring-exclusion-policy";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    /**
     * Run the exclusion policy demo and return one {@link CheckResult} per check performed.
     */
    @POST
    @Path("/run")
    public ExclusionPolicyDemoResponse run() {
        final ExpiringExclusionPolicy policy = new ExpiringExclusionPolicy();
        final List<CheckResult> results = new ArrayList<>();

        results.add(check(policy, 1, ALICE, "alice:2099-01-01"));  // future date → DENY
        results.add(check(policy, 2, ALICE, "alice:2020-01-01"));  // past date → ALLOW
        results.add(check(policy, 3, BOB, "alice:2099-01-01"));   // bob not in list → ALLOW
        results.add(check(policy, 4, ALICE, null));                // null exclusion → ALLOW
        results.add(check(policy, 5, ALICE, "alice"));             // plain ID → DENY (permanent)
        results.add(check(policy, 6, ALICE, "alice:not-a-date"));  // bad date → DENY (permanent)

        return new ExclusionPolicyDemoResponse(SCENARIO_ID, results);
    }

    private CheckResult check(final ExpiringExclusionPolicy policy, final int step,
                               final String actor, final String exclusionData) {
        final PolicyDecision decision = policy.check(actor, exclusionData);
        LOG.infof("[DEMO] Step %d: actor=%s exclusionData=%s → %s",
                step, actor, exclusionData, decision.denied() ? "DENY: " + decision.reason() : "ALLOW");
        return new CheckResult(actor, exclusionData, decision.denied(), decision.reason());
    }
}
