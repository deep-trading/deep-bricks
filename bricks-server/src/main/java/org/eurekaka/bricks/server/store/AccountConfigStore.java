package org.eurekaka.bricks.server.store;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.common.cryption.AES;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.eurekaka.bricks.common.cryption.AES.CBC_ALGORITHM;

/**
 * account config 数据存储操作
 */
public class AccountConfigStore {
    private final static String SQL_INSERT_ACCOUNT_CONFIG = "insert into account_config " +
            "(name, type, clz, listener_clz, api_clz, websocket, url, uid, iv, auth_key, auth_secret, " +
            "enabled, properties, priority, taker_rate, maker_rate) values " +
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private final static String SQL_UPDATE_ACCOUNT_CONFIG = "update account_config set " +
            "properties = ?, priority = ?, taker_rate = ?, maker_rate = ? where id = ?";
    private final static String SQL_UPDATE_ACCOUNT_CONFIG_ENABLED = "update account_config set " +
            "enabled = ? where id = ?";
    private final static String SQL_DELETE_ACCOUNT_CONFIG = "delete from account_config where id = ?";
    private final static String SQL_SELECT_ACCOUNT_CONFIG = "select * from account_config";
    private final static String SQL_SELECT_ACCOUNT_CONFIG_BY_ID = "select * from account_config where id = ?";

    public void store(AccountConfig accountConfig) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_ACCOUNT_CONFIG,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, accountConfig.getName());
            statement.setInt(2, accountConfig.getType());
            statement.setString(3, accountConfig.getClz());
            statement.setString(4, accountConfig.getListenerClz());
            statement.setString(5, accountConfig.getApiClz());
            statement.setString(6, accountConfig.getWebsocket());
            statement.setString(7, accountConfig.getUrl());
            statement.setString(8, accountConfig.getUid());
            // 生成iv
            IvParameterSpec iv = AES.generateIv();
            SecretKey key = AES.getKeyFromPassword(Utils.getAuthPassword(), Utils.getAuthSalt());
            statement.setString(9, Base64.getEncoder().encodeToString(iv.getIV()));
            statement.setString(10, AES.encrypt(CBC_ALGORITHM, accountConfig.getAuthKey(), key, iv));
            statement.setString(11, AES.encrypt(CBC_ALGORITHM, accountConfig.getAuthSecret(), key, iv));

            statement.setBoolean(12, accountConfig.isEnabled());
            statement.setString(13, Utils.mapper.writeValueAsString(accountConfig.getProperties()));
            statement.setInt(14, accountConfig.getPriority());
            statement.setDouble(15, accountConfig.getTakerRate());
            statement.setDouble(16, accountConfig.getMakerRate());

            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                accountConfig.setId(id);
            }
        } catch (Exception e) {
            throw new StoreException("insert account config failed", e);
        }
    }

    public List<AccountConfig> query() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_SELECT_ACCOUNT_CONFIG)) {
            ResultSet resultSet = statement.executeQuery();
            List<AccountConfig> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(parseAccountConfig(resultSet));
            }
            return result;
        } catch (Exception e) {
            throw new StoreException("query account config failed", e);
        }
    }

    public AccountConfig query(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_SELECT_ACCOUNT_CONFIG_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            AccountConfig result = null;
            while (resultSet.next()) {
                result = parseAccountConfig(resultSet);
            }
            return result;
        } catch (Exception e) {
            throw new StoreException("query account config failed", e);
        }
    }

    public void update(AccountConfig accountConfig) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_ACCOUNT_CONFIG)) {
            statement.setString(1, Utils.mapper.writeValueAsString(accountConfig.getProperties()));
            statement.setInt(2, accountConfig.getPriority());
            statement.setDouble(3, accountConfig.getTakerRate());
            statement.setDouble(4, accountConfig.getMakerRate());
            statement.setInt(5, accountConfig.getId());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new StoreException("update account config failed", e);
        }
    }

    public void updateEnabled(int id, boolean enabled) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_ACCOUNT_CONFIG_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update account config enabled failed", e);
        }
    }

    public void delete(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_ACCOUNT_CONFIG)) {
            statement.setInt(1, id);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("delete account config failed", e);
        }
    }

    private AccountConfig parseAccountConfig(ResultSet resultSet) throws Exception {
        String ivString = resultSet.getString("iv").trim();
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(ivString));
        SecretKey key = AES.getKeyFromPassword(Utils.getAuthPassword(), Utils.getAuthSalt());
        String authKey = AES.decrypt(CBC_ALGORITHM, resultSet.getString("auth_key"), key, iv);
        String authSecret = AES.decrypt(CBC_ALGORITHM, resultSet.getString("auth_secret"), key, iv);

        AccountConfig accountConfig = new AccountConfig(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getInt("type"),
                resultSet.getString("clz"),
                resultSet.getString("listener_clz"),
                resultSet.getString("api_clz"),
                resultSet.getString("websocket"),
                resultSet.getString("url"),
                resultSet.getString("uid"),
                authKey, authSecret,
                resultSet.getBoolean("enabled"));
        accountConfig.setProperties(Utils.mapper.readValue(
                resultSet.getString("properties"), new TypeReference<>() {}));
        accountConfig.setPriority(resultSet.getInt("priority"));
        accountConfig.setTakerRate(resultSet.getDouble("taker_rate"));
        accountConfig.setMakerRate(resultSet.getDouble("maker_rate"));

        return accountConfig;
    }

}
