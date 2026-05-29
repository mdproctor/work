package io.casehub.work.runtime.api;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.casehub.work.api.UnknownCapabilityException;

@Provider
public class UnknownCapabilityExceptionMapper implements ExceptionMapper<UnknownCapabilityException> {

    @Override
    public Response toResponse(final UnknownCapabilityException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "UNKNOWN_CAPABILITY",
                        "values", e.unknownIds(),
                        "message", "Unknown capabilities: " + e.unknownIds()))
                .build();
    }
}
