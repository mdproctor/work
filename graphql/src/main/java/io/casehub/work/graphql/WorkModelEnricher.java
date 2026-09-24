package io.casehub.work.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@McpDomain(value = "work/model", app = "work")
@ApplicationScoped
public class WorkModelEnricher implements ModelEnricher {

    @Override
    public String summary() {
        return "Work item lifecycle — create, claim, start, complete, reject, delegate, "
            + "suspend, resume, cancel, escalate work items. "
            + "Query inbox, filter by status/assignee/priority, subscribe to lifecycle events.";
    }

    @Override
    public Map<String, Object> state() {
        return Map.of();
    }
}
