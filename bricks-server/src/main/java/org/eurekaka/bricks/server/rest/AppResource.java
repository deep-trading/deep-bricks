package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.service.*;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

public class AppResource extends ResourceConfig {

    public AppResource(BrickContext brickContext) {
        property(ServerProperties.MOXY_JSON_FEATURE_DISABLE, true);
        registerClasses(HelloHandler.class);
        registerClasses(JacksonFeature.class);
        registerClasses(StoreExceptionMapper.class);
        registerClasses(RuntimeExceptionMapper.class);
        registerClasses(ServiceExceptionMapper.class);
        registerClasses(CORSFilter.class);

        AuthService authService = new AuthService(brickContext.config.getConfig("server"));
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(authService).to(AuthService.class);
            }
        });
        registerClasses(AuthenticationFilter.class);

        AccountConfigService accountConfigService = new AccountConfigService(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(accountConfigService).to(AccountConfigService.class);
            }
        });

        AccountAssetService assetService = new AccountAssetService(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(assetService).to(AccountAssetService.class);
            }
        });

        Info0Service infoService = new Info0Service(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(infoService).to(Info0Service.class);
            }
        });

        AccountService accountService = new AccountService(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(accountService).to(AccountService.class);
            }
        });

        OrderService orderService = new OrderService(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(orderService).to(OrderService.class);
            }
        });

        StrategyService strategyService = new StrategyService(brickContext);
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(strategyService).to(StrategyService.class);
            }
        });

        registerClasses(AccountAssetResource.class);
        registerClasses(AccountConfigResource.class);
        registerClasses(Info0Resource.class);
        registerClasses(StrategyConfigResource.class);
        registerClasses(AccountResource.class);
        registerClasses(OrderResource.class);
    }

}
