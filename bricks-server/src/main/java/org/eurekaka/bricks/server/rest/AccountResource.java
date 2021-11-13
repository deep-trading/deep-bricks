package org.eurekaka.bricks.server.rest;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.model.CommonResp;
import org.eurekaka.bricks.server.service.AccountService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("api/v2/account")
@Produces("application/json")
public class AccountResource {
    @Inject
    AccountService accountService;

    @GET
    @Path("profit")
    public Response getProfit() throws ServiceException {
        return Response.ok(accountService.getAccountProfit()).build();
    }

    @GET
    @Path("position")
    public Response getPositionValues() throws ServiceException {
        return Response.ok(accountService.getPositionValues()).build();
    }

    @GET
    @Path("history/position")
    public List<PositionValue> getPositionValues(@QueryParam("time") long time) throws ServiceException {
        return accountService.queryPositionValues(time);
    }

    @GET
    @Path("history/funding")
    public List<FundingValue> getFundingValues(@QueryParam("time") long time) throws ServiceException {
        return accountService.queryFundingValues(time);
    }

    @GET
    @Path("history/balance")
    public List<AccountProfit> getAccountBalances(@QueryParam("time") long time) throws ServiceException {
        return accountService.queryAccountBalances(time);
    }

    @POST
    @Path("risk")
    public Response updateLeverage(@QueryParam("account") String accountName,
                                   @QueryParam("future") String future,
                                   @QueryParam("leverage") int leverage) {
        if (accountService.updateRiskLimit(accountName, future, leverage)) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new CommonResp<>(400, "failed to update the leverage")).build();
        }
    }

    @GET
    @Path("risk")
    public RiskLimitValue getAccountRiskLimit(@QueryParam("account") String accountName) throws ServiceException {
        RiskLimitValue value = accountService.getRiskLimitValue(accountName);
        if (value == null) {
            throw new ServiceException("no account available");
        }
        return value;
    }

    @POST
    @Path("wallet/transfer")
    public Response transfer1(@QueryParam("from_account") String fromAccount,
                              @QueryParam("to_account") String toAccount,
                              @QueryParam("asset") String asset,
                              @QueryParam("amount") double amount) throws ServiceException {
        accountService.transfer(fromAccount, toAccount, asset, amount);
        return Response.ok().build();
    }

    @POST
    @Path("wallet/internal_transfer")
    public Response transfer2(@QueryParam("account") String account,
                              @QueryParam("type") int type,
                              @QueryParam("asset") String asset,
                              @QueryParam("amount") double amount) throws ServiceException {
        accountService.transfer(account, type, asset, amount);
        return Response.ok().build();
    }

    @POST
    @Path("wallet/withdraw")
    public Response withdraw(@QueryParam("from_account") String fromAccount,
                             @QueryParam("to_account") String toAccount,
                             @QueryParam("asset") String asset,
                             @QueryParam("amount") double amount) throws ServiceException {
        accountService.withdraw(fromAccount, toAccount, asset, amount);
        return Response.ok().build();
    }

    @GET
    @Path("wallet/asset_records")
    public List<AccountAssetRecord> getAssetRecords(@QueryParam("account") String account,
                                                    @QueryParam("type") int type,
                                                    @QueryParam("start") long start,
                                                    @QueryParam("stop") long stop,
                                                    @QueryParam("limit") int limit) throws ServiceException {
        return accountService.getAssetRecords(account, type, start, stop, limit);
    }

}
