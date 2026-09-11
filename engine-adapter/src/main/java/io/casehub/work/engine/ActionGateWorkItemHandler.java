/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.work.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.engine.common.spi.ActionGateScheduleRequest;
import io.casehub.engine.common.spi.ActionGateScheduler;
import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.ParentRole;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.spi.WorkItemCreator;
import java.util.logging.Logger;

import java.time.Instant;
import java.util.Set;

/**
 * Creates a WorkItem when an action gate fires.
 *
 * <p>Implements {@link ActionGateScheduler} — discovered by the engine runtime via CDI
 * {@code Instance<ActionGateScheduler>}.
 * Creates a WorkItem with:
 *
 * <ul>
 *   <li>callerRef: {@code "case:{caseId}/gate:{gateId}"} — routes back to {@link
 *       ActionGateCompletionApplier}
 *   <li>title: {@link io.casehub.api.spi.RiskDecision.GateRequired#reason()}
 *   <li>candidateGroups: from the gate decision (CSV)
 *   <li>expiresAt: from {@code expiresIn} (if set)
 *   <li>payload: full PlannedAction as JSON (approver sees what the agent proposed)
 * </ul>
 *
 * <p>No PlanItem, no BlackboardRegistry — gate WorkItems are not backed by CMMN plan items. Refs
 * engine#402.
 */
public class ActionGateWorkItemHandler implements ActionGateScheduler {

    private static final Logger       LOG    = Logger.getLogger(ActionGateWorkItemHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkItemCreator workItemCreator;

    public ActionGateWorkItemHandler(final WorkItemCreator workItemCreator) {
        this.workItemCreator = workItemCreator;
    }

    private static String buildPayload(final ActionGateScheduleRequest event) {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("description", event.plannedAction().description());
        root.put("actionType", event.plannedAction().actionType());
        root.put("reversible", event.gateRequired().reversible());
        root.set("context", MAPPER.valueToTree(event.plannedAction().parameters()));
        try {
            return MAPPER.writeValueAsString(root);
        } catch (final JsonProcessingException e) {
            LOG.warning("Failed to serialize gate payload for gateId=" + event.gateId() + " — using null: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void schedule(final ActionGateScheduleRequest event) {
        final String callerRef = GateRef.encode(event.caseId(), event.gateId());
        final Instant expiresAt =
                event.gateRequired().expiresIn() != null
                ? Instant.now().plus(event.gateRequired().expiresIn())
                : null;

        final Set<String> groups = event.resolvedCandidateGroups();
        final String candidateGroupsCsv =
                (groups == null || groups.isEmpty())
                ? null
                : groups.stream().sorted().collect(java.util.stream.Collectors.joining(","));

        final WorkItemCreateRequest request =
                WorkItemCreateRequest.builder()
                                     .title(event.gateRequired().reason())
                                     .candidateGroups(candidateGroupsCsv)
                                     .createdBy("casehub-engine")
                                     .payload(buildPayload(event))
                                     .expiresAt(expiresAt)
                                     .callerRef(callerRef)
                                     .scope(event.gateRequired().scope())
                                     .tenancyId(event.tenancyId())
                                     .resolutionTypeName(event.resolutionTypeName())
                                     .build();

        if (event.gateRequired().quorum() != null) {
            final var q = event.gateRequired().quorum();
            final var config = new MultiInstanceConfig(
                    q.instances(),
                    q.required(),
                    ParentRole.COORDINATOR,
                    "pool",
                    q.onThresholdReached() != null
                    ? io.casehub.work.api.OnThresholdReached.valueOf(q.onThresholdReached().name())
                    : null,
                    q.allowSameAssignee(),
                    null);
            workItemCreator.createMultiInstance(request, config);
            LOG.info("Gate multi-instance group created: caseId=" + event.caseId()
                    + " gateId=" + event.gateId() + " instances=" + q.instances()
                    + " required=" + q.required());
        } else {
            workItemCreator.create(request);
            LOG.info("Gate WorkItem created: caseId=" + event.caseId()
                    + " gateId=" + event.gateId() + " callerRef=" + callerRef
                    + " expiresAt=" + expiresAt);
        }
    }
}
