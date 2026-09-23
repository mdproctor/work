package io.casehub.work.runtime.rest;

import io.casehub.work.runtime.rest.core.SlaAdminCore;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;

@Path("/workitems/admin/sla-config")
public class SlaAdminResource {

    @Inject
    SlaAdminCore core;

    @POST
    @Path("/reload")
    public Map<String, Boolean> reload() {
        return core.reload();
    }
}
