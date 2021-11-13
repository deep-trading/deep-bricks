package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.server.model.CommonResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class StoreExceptionMapper implements ExceptionMapper<StoreException> {
    private static final Logger logger = LoggerFactory.getLogger(StoreExceptionMapper.class);

    @Override
    public Response toResponse(StoreException e) {
        logger.error("store exception error", e);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new CommonResp<>(400, e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
