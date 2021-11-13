package org.eurekaka.bricks.market.store;

import org.eurekaka.bricks.api.StrategyStore;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.market.model.StrategyValue02;
import org.eurekaka.bricks.server.store.DatabaseStore;

import java.sql.*;

public class StrategyValueStore02 implements StrategyStore<StrategyValue02> {
    private static final String SQL_INSERT_ACCOUNT_VALUE = "insert into account_value (name, value, time) " +
            "VALUES (?, ?, ?)";

    private static final String SQL_QUERY_ACCOUNT_VALUE = "select name, value, time from account_value " +
            "where name = ? order by time desc limit 1";

    @Override
    public void store(StrategyValue02 value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_ACCOUNT_VALUE)) {
            statement.setString(1, value.name);
            statement.setDouble(2, value.value);
            statement.setTimestamp(3, new Timestamp(value.time));
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("failed to insert account value: " + value, e);
        }
    }

    @Override
    public StrategyValue02 query(String name) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_ACCOUNT_VALUE)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            StrategyValue02 value = null;
            while (resultSet.next()) {
                value = new StrategyValue02(
                        resultSet.getString("name"),
                        resultSet.getDouble("value"),
                        resultSet.getTimestamp("time").getTime());
            }
            return value;
        } catch (SQLException e) {
            throw new StoreException("failed to query account value: " + name, e);
        }
    }
}
