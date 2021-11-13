package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.server.service.Info0Service;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("api/v2/info")
@Produces("application/json")
public class Info0Resource {

    @Inject
    private Info0Service infoService;

    @GET
    public List<Info0> getInfos(@QueryParam("type") int type) throws ServiceException {
//        if (type == 0) {
//            throw new ServiceException("info0 type is missing");
//        }
        return infoService.query().stream()
                .filter(e -> type == 0 || e.getType() == type)
                .collect(Collectors.toList());
    }

    @PUT
    public Info0 putInfo(Info0 info) throws ServiceException {
//        if (type == 0 && info.getType() == 0) {
//            throw new ServiceException("info0 type is missing");
//        }
        if (info.getId() != 0) {
            throw new ServiceException("store new info id should be 0");
        }
        if (info.getType() < 1) {
            throw new ServiceException("info type should >= 1");
        }

        infoService.store(info);
        return info;
    }

    @POST
    public Response updateInfo(Info0 info) throws ServiceException {
        if (info.getId() == 0) {
            throw new ServiceException("update info id should not be 0");
        }
        infoService.update(info);
        return Response.ok().build();
    }

    @DELETE
    public Response deleteInfo(@QueryParam("id") int id) throws ServiceException {
        infoService.delete(id);
        return Response.ok().build();
    }

    @POST
    @Path("enable")
    public Response enableFutureInfo(@QueryParam("id") int id) throws ServiceException {
        infoService.enable(id);
        return Response.ok().build();
    }

    @POST
    @Path("disable")
    public Response disableFutureInfo(@QueryParam("id") int id) throws ServiceException {
        infoService.disable(id);
        return Response.ok().build();
    }
}
