package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.server.service.OrderService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("api/v2/order")
@Produces("application/json")
public class OrderResource {


    @Inject
    private OrderService orderService;

    @POST
    @Path("market")
    public Response makeMarketOrder(@QueryParam("account") String account,
                                    @QueryParam("name") String name,
                                    @QueryParam("size") double size) throws ServiceException {
        orderService.makeMarketOrder(account, name, size);
        return Response.ok().build();
    }

    @POST
    @Path("limit")
    public Response makeLimitOrder(@QueryParam("account") String account,
                                   @QueryParam("name") String name,
                                   @QueryParam("size") double size,
                                   @QueryParam("price") double price) throws ServiceException {
        orderService.makeLimitOrder(account, name, size, price);
        return Response.ok().build();
    }

    @GET
    @Path("current")
    public Response getCurrentOrders(@QueryParam("account") String account,
                                     @QueryParam("name") String name) throws ServiceException {
        return Response.ok(orderService.getCurrentOrders(account, name)).build();
    }

    @DELETE
    public Response cancelOrder(@QueryParam("account") String account,
                                @QueryParam("name") String name,
                                @QueryParam("order_id") String orderId) throws ServiceException {
        return Response.ok(orderService.cancelOrder(account, name, orderId)).build();
    }

    @GET
    @Path("history")
    public Response getHistoryOrders(@QueryParam("account") String account,
                                     @QueryParam("name") String name,
                                     @QueryParam("start") long start,
                                     @QueryParam("stop") long stop,
                                     @QueryParam("limit") @DefaultValue("20") int limit) throws ServiceException {
        return Response.ok(orderService.getHistoryOrders(account, name, start, stop, limit)).build();
    }

}
