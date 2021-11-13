package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.model.ReportEvent;
import org.eurekaka.bricks.common.util.WeChatReporter;
import org.eurekaka.bricks.server.model.CommonResp;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Set;

@Path("api/v1/monitor")
@Produces("application/json")
public class MonitorResource {

    @GET
    public CommonResp<String> getToken() {
        return new CommonResp<>(200, WeChatReporter.getToken());
    }

    @PUT
    @Path("wechat")
    public Response sendTestMessage() {
        Set<String> users = WeChatReporter.updateUsers();
        WeChatReporter.report(String.valueOf(System.currentTimeMillis()),
                new ReportEvent(ReportEvent.EventType.MONITOR_TEST,
                        ReportEvent.EventLevel.NORMAL, "sending works."));

        return Response.ok().entity(new CommonResp<>(200, users)).build();
    }

}
