package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.service.StrategyService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("api/v2/strategy")
public class StrategyConfigResource {

    @Inject
    private StrategyService strategyService;

    @GET
    public Response queryStrategyConfigs() throws ServiceException {
        return Response.ok(strategyService.query()).build();
    }

    @PUT
    public Response putStrategyConfig(StrategyConfig strategyConfig) throws ServiceException {
        if (strategyConfig.getId() != 0) {
            throw new ServiceException("store strategy config id must be 0, id: " + strategyConfig.getId());
        }
        strategyService.store(strategyConfig);
        return Response.ok(strategyConfig).build();
    }

    @POST
    public Response updateStrategyConfig(StrategyConfig strategyConfig) throws ServiceException {
        if (strategyConfig.getId() == 0) {
            throw new ServiceException("update config id should not be 0");
        }
        strategyService.update(strategyConfig);
        return Response.ok().build();
    }

    @DELETE
    public Response deleteStrategyConfig(@QueryParam("id") int id) throws ServiceException {
        strategyService.delete(id);
        return Response.ok().build();
    }

    @POST
    @Path("enable")
    public Response enableStrategyConfig(@QueryParam("id") int id) throws ServiceException {
        strategyService.enable(id);
        return Response.ok().build();
    }

    @POST
    @Path("disable")
    public Response disableStrategyConfig(@QueryParam("id") int id) throws ServiceException {
        strategyService.disable(id);
        return Response.ok().build();
    }

}
