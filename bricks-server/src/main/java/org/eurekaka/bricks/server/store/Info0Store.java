package org.eurekaka.bricks.server.store;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.common.model.InfoStore;
import org.eurekaka.bricks.common.util.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Info0Store implements InfoStore<Info0> {
    private final static String SQL_QUERY_INFO = "select * from symbol_info";
    private final static String SQL_QUERY_INFO_BY_ID = "select * from symbol_info where id = ?";
    private final static String SQL_UPDATE_INFO = "update symbol_info set price_precision = ?, " +
            "size_precision = ?, properties = ? where id = ?";
    private final static String SQL_INSERT_INFO = "insert into symbol_info " +
            "(name, account, symbol, type, price_precision, size_precision, properties, enabled) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_INFO = "delete from symbol_info where id = ?";
    private static final String SQL_UPDATE_INFO_ENABLED = "update symbol_info set enabled = ? where id = ?";

    @Override
    public void store(Info0 info) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_INFO,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, info.getName());
            statement.setString(2, info.getAccount());
            statement.setString(3, info.getSymbol());
            statement.setInt(4, info.getType());
            statement.setDouble(5, info.getPricePrecision());
            statement.setDouble(6, info.getSizePrecision());
            statement.setString(7, Utils.mapper.writeValueAsString(info.getProperties()));
            statement.setBoolean(8, info.isEnabled());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                info.setId(id);
            }
        } catch (Exception e) {
            throw new StoreException("insert info failed", e);
        }
    }

    @Override
    public List<Info0> query() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_INFO)) {
            ResultSet resultSet = statement.executeQuery();
            List<Info0> infoList = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, String> properties = Utils.mapper.readValue(
                        resultSet.getString("properties"), new TypeReference<>() {});
                Info0 futureInfo = new Info0(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getInt("type"),
                        resultSet.getDouble("price_precision"),
                        resultSet.getDouble("size_precision"),
                        resultSet.getBoolean("enabled"),
                        properties);
                infoList.add(futureInfo);
            }
            return infoList;
        } catch (Exception e) {
            throw new StoreException("query info failed", e);
        }
    }

    @Override
    public Info0 queryInfo(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_INFO_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            Info0 info = null;
            while (resultSet.next()) {
                Map<String, String> properties = Utils.mapper.readValue(
                        resultSet.getString("properties"), new TypeReference<>() {});
                info = new Info0(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        resultSet.getString("account"),
                        resultSet.getInt("type"),
                        resultSet.getDouble("price_precision"),
                        resultSet.getDouble("size_precision"),
                        resultSet.getBoolean("enabled"), properties);
            }
            return info;
        } catch (Exception e) {
            throw new StoreException("query info failed", e);
        }
    }

    @Override
    public void update(Info0 info) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_INFO)) {
            statement.setDouble(1, info.getPricePrecision());
            statement.setDouble(2, info.getSizePrecision());
            statement.setString(3, Utils.mapper.writeValueAsString(info.getProperties()));
            statement.setInt(4, info.getId());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new StoreException("update info failed", e);
        }
    }

    @Override
    public void delete(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_INFO)) {
            statement.setInt(1, id);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("delete info failed", e);
        }
    }

    @Override
    public void updateEnabled(int id, boolean enabled) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_INFO_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update info enabled failed", e);
        }
    }
}
