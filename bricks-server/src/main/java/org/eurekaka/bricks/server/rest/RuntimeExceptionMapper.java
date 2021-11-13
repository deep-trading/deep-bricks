package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.server.model.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;

public class RuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {
    private final static Logger logger = LoggerFactory.getLogger(RuntimeExceptionMapper.class);

    @Context
    private ResourceInfo resourceInfo;

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(RuntimeException ex) {
        logger.error("rest error, uri; {}", uriInfo.getRequestUri(), ex);

        Response.StatusType type = getStatusType(ex);

        return Response.status(type.getStatusCode())
                .entity(new CommonResp<>(501, ex.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response.StatusType getStatusType(Throwable ex) {
        if (ex instanceof WebApplicationException) {
            return((WebApplicationException)ex).getResponse().getStatusInfo();
        } else {
            return Response.Status.INTERNAL_SERVER_ERROR;
        }
    }
}
