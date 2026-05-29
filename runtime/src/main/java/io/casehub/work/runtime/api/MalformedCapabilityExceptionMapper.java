package io.casehub.work.runtime.api;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.casehub.work.api.MalformedCapabilityException;

@Provider
public class MalformedCapabilityExceptionMapper implements ExceptionMapper<MalformedCapabilityException> {

    @Override
    public Response toResponse(final MalformedCapabilityException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "MALFORMED_CAPABILITY",
                        "values", e.badValues(),
                        "message", "Capability id must be lowercase kebab-case"))
                .build();
    }
}
