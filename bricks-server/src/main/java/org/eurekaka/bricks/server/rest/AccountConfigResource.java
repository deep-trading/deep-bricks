package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.manager.AccountAssetState;
import org.eurekaka.bricks.server.manager.AccountConfigState;
import org.eurekaka.bricks.server.manager.AccountManagerImpl;
import org.eurekaka.bricks.server.model.CommonResp;
import org.eurekaka.bricks.server.service.AccountConfigService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;


@Path("api/v2/account/config")
@Produces("application/json")
public class AccountConfigResource {

    @Inject
    AccountConfigService service;

    @GET
    public List<AccountConfig> getAccountConfigs() throws ServiceException {
        return service.query();
    }

    @PUT
    public AccountConfig putAccountConfig(AccountConfig accountConfig) throws ServiceException {
        service.store(accountConfig);
        return accountConfig;
    }

    @POST
    public AccountConfig updateAccountConfig(AccountConfig accountConfig) throws ServiceException {
        service.update(accountConfig);
        return accountConfig;
    }

    @DELETE
    public Response deleteAccountConfig(@QueryParam("id") int id) throws ServiceException {
        service.delete(id);
        return Response.ok().build();
    }

    @POST
    @Path("enable")
    public Response enableAccountConfig(@QueryParam("id") int id) throws ServiceException {
        service.enable(id);
        return Response.ok().build();
    }

    @POST
    @Path("disable")
    public Response disableAccountConfig(@QueryParam("id") int id) throws ServiceException {
        service.disable(id);
        return Response.ok().build();
    }

}
