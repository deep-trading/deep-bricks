package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.server.service.AuthService;
import org.eurekaka.bricks.server.model.CommonResp;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;

@Provider
public class AuthenticationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    private AuthService authService;

    private static final String AUTHORIZATION_PROPERTY = "Authorization";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!requestContext.getMethod().equals("OPTIONS") &&
                requestContext.getUriInfo().getPath().startsWith("api/")) {
            //Get request headers
            final MultivaluedMap<String, String> headers = requestContext.getHeaders();

            //Fetch authorization header
            final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);

            //If no authorization information present; block access
            if(authorization == null || authorization.isEmpty()) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .entity(new CommonResp<>(401,
                                "You cannot access this resource, missing Authorization")).build());
                return;
            }

            if (!authService.auth(authorization.get(0))) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .entity(new CommonResp<>(401,
                                "You cannot access this resource, token error")).build());
            }
        }
    }
}
