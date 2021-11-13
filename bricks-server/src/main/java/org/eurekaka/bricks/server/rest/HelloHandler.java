package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.util.WeChatReporter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class HelloHandler {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("hello")
    public Response hello() {
        return Response.status(200).entity("hello hedging").build();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("monitor/users")
    public String updateMonitorUsers() {
        WeChatReporter.updateUsers();
        return "users updated.";
    }

}
