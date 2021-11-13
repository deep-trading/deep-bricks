package org.eurekaka.bricks.server.store;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.common.util.Utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StrategyConfigStore {
    private final static String SQL_QUERY_CONFIG = "select * from strategy_config";
    private final static String SQL_QUERY_CONFIG_BY_ID = "select * from strategy_config where id = ?";
    private final static String SQL_UPDATE_CONFIG = "update strategy_config set properties = ? where id = ?";
    private final static String SQL_INSERT_CONFIG = "insert into strategy_config " +
            "(name, clz, info_name, priority, properties, enabled) values (?, ?, ?, ?, ?, ?)";
    private static final String SQL_DELETE_CONFIG = "delete from strategy_config where id = ?";
    private static final String SQL_UPDATE_CONFIG_ENABLED = "update strategy_config set enabled = ? where id = ?";

    public void store(StrategyConfig strategyConfig) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_CONFIG,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, strategyConfig.getName());
            statement.setString(2, strategyConfig.getClz());
            statement.setString(3, strategyConfig.getInfoName());
            statement.setInt(4, strategyConfig.getPriority());
            statement.setString(5, Utils.mapper.writeValueAsString(strategyConfig.getProperties()));
            statement.setBoolean(6, strategyConfig.isEnabled());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                strategyConfig.setId(id);
            }
        } catch (Exception e) {
            throw new StoreException("insert strategy config failed", e);
        }
    }

    public List<StrategyConfig> query() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_CONFIG)) {
            ResultSet resultSet = statement.executeQuery();
            List<StrategyConfig> configList = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, String> properties = Utils.mapper.readValue(
                        resultSet.getString("properties"), new TypeReference<>() {});
                StrategyConfig config = new StrategyConfig(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("clz"),
                        resultSet.getString("info_name"),
                        resultSet.getInt("priority"),
                        resultSet.getBoolean("enabled"),
                        properties);
                configList.add(config);
            }
            return configList;
        } catch (Exception e) {
            throw new StoreException("query strategy config failed", e);
        }
    }

    public StrategyConfig query(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_CONFIG_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            StrategyConfig config = null;
            while (resultSet.next()) {
                Map<String, String> properties = Utils.mapper.readValue(
                        resultSet.getString("properties"), new TypeReference<>() {});
                config = new StrategyConfig(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("clz"),
                        resultSet.getString("info_name"),
                        resultSet.getInt("priority"),
                        resultSet.getBoolean("enabled"),
                        properties);
            }
            return config;
        } catch (Exception e) {
            throw new StoreException("query strategy config failed", e);
        }
    }

    public void update(StrategyConfig strategyConfig) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_CONFIG)) {
            statement.setString(1, Utils.mapper.writeValueAsString(strategyConfig.getProperties()));
            statement.setInt(2, strategyConfig.getId());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new StoreException("update strategy config failed", e);
        }
    }

    public void delete(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_CONFIG)) {
            statement.setInt(1, id);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("delete strategy config failed", e);
        }
    }

    public void updateEnabled(int id, boolean enabled) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_CONFIG_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update strategy config enabled failed", e);
        }
    }

}
