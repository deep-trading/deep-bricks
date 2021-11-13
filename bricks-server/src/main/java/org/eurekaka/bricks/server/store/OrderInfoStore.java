package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.InfoStore;
import org.eurekaka.bricks.server.model.OrderInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderInfoStore implements InfoStore<OrderInfo> {
    private static final String SQL_QUERY_FUTURE_INFO = "select * from future_symbol";
    private static final String SQL_QUERY_FUTURE_INFO_BY_ID = "select * from future_symbol where id = ?";
    private static final String SQL_UPDATE_FUTURE_INFO = "update future_symbol set depth_qty = ?, side = ?, " +
            "price_precision = ?, size_precision = ? where id = ? ";
    private static final String SQL_INSERT_FUTURE_INFO = "insert into future_symbol " +
            "(name, account, symbol, depth_qty, side, price_precision, size_precision, enabled) " +
            "values(?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_FUTURE_INFO = "delete from future_symbol where id = ?";
    private static final String SQL_UPDATE_FUTURE_INFO_ENABLED = "update future_symbol set enabled = ? where id = ?";

    @Override
    public void store(OrderInfo info) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_FUTURE_INFO,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, info.getName());
            statement.setString(2, info.getAccount());
            statement.setString(3, info.getSymbol());
            statement.setInt(4, info.getDepthQty());
            statement.setString(5, info.getSide().name());
            statement.setDouble(6, info.getPricePrecision());
            statement.setDouble(7, info.getSizePrecision());
            statement.setBoolean(8, info.isEnabled());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                info.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("insert future info failed", e);
        }
    }

    @Override
    public List<OrderInfo> query() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_FUTURE_INFO)) {
            ResultSet resultSet = statement.executeQuery();
            List<OrderInfo> infoList = new ArrayList<>();
            while (resultSet.next()) {
                OrderInfo futureInfo = new OrderInfo(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("price_precision"),
                        resultSet.getDouble("size_precision"),
                        resultSet.getInt("depth_qty"),
                        OrderSide.valueOf(resultSet.getString("side")),
                        resultSet.getBoolean("enabled"));
                infoList.add(futureInfo);
            }
            return infoList;
        } catch (SQLException e) {
            throw new StoreException("query future info failed", e);
        }
    }

    @Override
    public OrderInfo queryInfo(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_FUTURE_INFO_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            OrderInfo info = null;
            while (resultSet.next()) {
                info = new OrderInfo(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getDouble("price_precision"),
                        resultSet.getDouble("size_precision"),
                        resultSet.getInt("depth_qty"),
                        OrderSide.valueOf(resultSet.getString("side")),
                        resultSet.getBoolean("enabled"));
            }
            return info;
        } catch (SQLException e) {
            throw new StoreException("query future info failed", e);
        }
    }

    @Override
    public void update(OrderInfo info) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_FUTURE_INFO)) {
            statement.setInt(1, info.getDepthQty());
            statement.setString(2, info.getSide().name());
            statement.setDouble(3, info.getPricePrecision());
            statement.setDouble(4, info.getSizePrecision());
            statement.setInt(5, info.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update future info failed", e);
        }
    }

    @Override
    public void delete(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_FUTURE_INFO)) {
            statement.setInt(1, id);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("delete future info failed", e);
        }
    }

    @Override
    public void updateEnabled(int id, boolean enabled) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_FUTURE_INFO_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update future info enabled failed", e);
        }
    }
}
