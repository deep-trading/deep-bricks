package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountBalance;
import org.eurekaka.bricks.common.model.AccountProfit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BalanceStore {
    private static final String SQL_INSERT_CHECKING_BALANCE = "insert into checking_balance " +
            "(asset, account, size, price, result, time) values (?, ?, ?, ?, ?, ?)";

    private static final String SQL_QUERY_CHECKING_BALANCE = "select * from checking_balance " +
            "where date_trunc('minute', time) = ?";

    private static final String SQL_INSERT_CHECKING_PROFIT = "insert into checking_profit " +
            "(asset, account, last_size, size, price, result, time) values (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_QUERY_CHECKING_PROFIT = "select * from checking_profit " +
            "where date_trunc('minute', time) = ?";

    @Deprecated
    public void storeAccountBalance(AccountBalance value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_CHECKING_BALANCE)) {
            statement.setString(1, value.asset);
            statement.setString(2, value.account);
            statement.setDouble(3, value.getSize());
            statement.setDouble(4, value.getPrice());
            statement.setLong(5, value.getQuantity());
            statement.setTimestamp(6, new Timestamp(value.getTime()));
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("failed to insert account balance: " + value, e);
        }
    }

    @Deprecated
    public List<AccountBalance> queryAccountBalance(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_CHECKING_BALANCE)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<AccountBalance> balanceValues = new ArrayList<>();
            while (resultSet.next()) {
                balanceValues.add(new AccountBalance(
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("result"),
                        resultSet.getTimestamp("time").getTime()));
            }
            return balanceValues;
        } catch (SQLException e) {
            throw new StoreException("failed to query account balance at time " + time, e);
        }
    }

    public void storeAccountProfit(AccountProfit value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_CHECKING_PROFIT)) {
            statement.setString(1, value.asset);
            statement.setString(2, value.account);
            statement.setDouble(3, value.getLastSize());
            statement.setDouble(4, value.getSize());
            statement.setDouble(5, value.getPrice());
            statement.setLong(6, value.getQuantity());
            statement.setTimestamp(7, new Timestamp(value.getTime()));
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("failed to insert account profit: " + value, e);
        }
    }

    public List<AccountProfit> queryAccountProfit(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_CHECKING_PROFIT)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<AccountProfit> balanceValues = new ArrayList<>();
            while (resultSet.next()) {
                AccountProfit profit = new AccountProfit(
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("last_size"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("result"));
                profit.setTime(resultSet.getTimestamp("time").getTime());
                balanceValues.add(profit);
            }
            return balanceValues;
        } catch (SQLException e) {
            throw new StoreException("failed to query account balance at time " + time, e);
        }
    }

}
