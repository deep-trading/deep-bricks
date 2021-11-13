package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.FundingValue;
import org.eurekaka.bricks.common.model.PositionValue;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 期货特有的仓位，资金费率等信息快照存储
 */
public class FutureStore {
    private static final String SQL_QUERY_POSITION_VALUE = "select * from position_value where " +
            "name = ? and time >= ? and time < ?";
    private static final String SQL_INSERT_POSITION_VALUE = "insert into position_value " +
            "(name, symbol, account, size, price, result, entry_price, unrealized_pnl, time) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_POSITION_VALUE_BY_TIME = "select * from position_value " +
            "where date_trunc('minute', time) = ?";

    public void storePositionValue(PositionValue value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_POSITION_VALUE)) {
            statement.setString(1, value.getName());
            statement.setString(2, value.getSymbol());
            statement.setString(3, value.getAccount());
            statement.setDouble(4, value.getSize());
            statement.setDouble(5, value.getPrice());
            statement.setLong(6, value.getQuantity());
            statement.setDouble(7, value.getEntryPrice());
            statement.setDouble(8, value.getUnPnl());
            statement.setTimestamp(9, new Timestamp(value.getTime()));
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("insert position value failed", e);
        }
    }

    public List<PositionValue> queryPositionValue(String name, long start, long end) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_POSITION_VALUE)) {
            statement.setString(1, name);
            statement.setTimestamp(2, new Timestamp(start));
            statement.setTimestamp(3, new Timestamp(end));
            ResultSet resultSet = statement.executeQuery();
            List<PositionValue> valueList = new ArrayList<>();
            while (resultSet.next()) {
                PositionValue value = new PositionValue(
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("result"),
                        resultSet.getDouble("entry_price"),
                        resultSet.getDouble("unrealized_pnl"),
                        resultSet.getTimestamp("time").getTime());
                valueList.add(value);
            }
            return valueList;
        } catch (SQLException e) {
            throw new StoreException("query position value failed", e);
        }
    }

    public List<PositionValue> queryPositionValueByTime(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_POSITION_VALUE_BY_TIME)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<PositionValue> valueList = new ArrayList<>();
            while (resultSet.next()) {
                PositionValue value = new PositionValue(
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("result"),
                        resultSet.getDouble("entry_price"),
                        resultSet.getDouble("unrealized_pnl"),
                        resultSet.getTimestamp("time").getTime());
                valueList.add(value);
            }
            return valueList;
        } catch (SQLException e) {
            throw new StoreException("query position value by time failed", e);
        }
    }


    private static final String SQL_INSERT_FUNDING_VALUE = "insert into funding " +
            "(name, symbol, account, value, rate, time) values (?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_LAST_FUNDING_VALUE = "select * from funding " +
            "where account = ? order by time desc limit 1";
    private static final String SQL_QUERY_FUNDING_BY_TIME = "select * from funding " +
            "where date_trunc('minute', time) = ?";
    private static final String SQL_QUERY_FUNDING_FROM_TIME = "select name, symbol, account, " +
            "sum(value) as value, sum(rate) as rate from funding " +
            "where time >= ? group by name, symbol, account";

    public void storeFundingValue(FundingValue value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_FUNDING_VALUE,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, value.getName());
            statement.setString(2, value.getSymbol());
            statement.setString(3, value.getAccount());
            statement.setDouble(4, value.getValue());
            statement.setDouble(5, value.getRate());
            statement.setTimestamp(6, new Timestamp(value.getTime()));
            statement.execute();
//            ResultSet resultSet = statement.getGeneratedKeys();
        } catch (SQLException e) {
            throw new StoreException("failed to insert funding value: " + value, e);
        }
    }

    public List<FundingValue> queryFundingValueByTime(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_FUNDING_BY_TIME)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<FundingValue> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(new FundingValue(
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("value"),
                        resultSet.getDouble("rate"),
                        resultSet.getTimestamp("time").getTime()));
            }
            return values;
        } catch (SQLException e) {
            throw new StoreException("query last funding value failed", e);
        }
    }

    public List<FundingValue> queryFundingValueFromTime(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_FUNDING_FROM_TIME)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<FundingValue> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(new FundingValue(
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("value"),
                        resultSet.getDouble("rate"),
                        time));
            }
            return values;
        } catch (SQLException e) {
            throw new StoreException("query last funding value failed", e);
        }
    }

}
