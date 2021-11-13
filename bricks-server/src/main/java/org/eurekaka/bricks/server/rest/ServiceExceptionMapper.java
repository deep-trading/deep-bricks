package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.server.model.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class ServiceExceptionMapper implements ExceptionMapper<ServiceException> {
    private static final Logger logger = LoggerFactory.getLogger(ServiceExceptionMapper.class);

    @Override
    public Response toResponse(ServiceException e) {
        logger.error("service exception", e);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new CommonResp<>(400, e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
