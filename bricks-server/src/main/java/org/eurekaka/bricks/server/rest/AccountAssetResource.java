package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;
import org.eurekaka.bricks.server.service.AccountAssetService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("api/v2/asset")
@Produces("application/json")
public class AccountAssetResource {

    @Inject
    AccountAssetService assetService;

    @GET
    public List<AssetBaseValue> getAssetBaseValues() throws ServiceException {
        return assetService.query();
    }

    @GET
    @Path("history")
    public List<AssetHistoryBaseValue> queryAssetHistoryValues(@QueryParam("start") long start,
            @QueryParam("stop") long stop, @QueryParam("limit") @DefaultValue("10") int limit)
            throws ServiceException {
        return assetService.queryHistoryAsset(start, stop, limit);
    }

    @PUT
    public AssetBaseValue putAssetBaseValue(AssetBaseValue value) throws ServiceException {
        assetService.store(value);
        return value;
    }

    @POST
    public Response updateAssetBaseValue(AssetHistoryBaseValue historyBaseValue) throws ServiceException {
        assetService.update(historyBaseValue);
        return Response.ok().build();
    }


    @DELETE
    public Response deleteAssetBaseValue(@QueryParam("id") int id) throws ServiceException {
        assetService.delete(id);
        return Response.ok().build();
    }

    @POST
    @Path("enable")
    public Response enableAssetBaseValue(@QueryParam("id") int id) throws ServiceException {
        assetService.enable(id);
        return Response.ok().build();
    }

    @POST
    @Path("disable")
    public Response disableAssetBaseValue(@QueryParam("id") int id) throws ServiceException {
        // 注意:: 需要在strategy中处理无法获取初始值的情况
        assetService.disable(id);
        return Response.ok().build();
    }
}
