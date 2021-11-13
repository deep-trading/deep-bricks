package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountBalance;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 所有账户余额或者资产的数据库记录管理
 * 账户余额的变动，都需要创建一条记录在history表内，不能直接update或者store
 */
public class AssetBaseValueStore {
    private final static String SQL_INSERT_ASSET_BASE = "insert into asset_base " +
            "(asset, account, value, enabled) values (?, ?, ?, ?)";
    private final static String SQL_QUERY_ASSET_BASE = "select * from asset_base";
    private final static String SQL_QUERY_ASSET_BASE_BY_ID = "select * from asset_base where id = ?";
    private final static String SQL_DELETE_ASSET_BASE = "delete from asset_base where id = ?";
    private final static String SQL_UPDATE_ASSET_BASE_ENABLED = "update asset_base set enabled = ? where id = ?";
    private final static String SQL_UPDATE_ASSET_BASE = "update asset_base " +
            "set value = ? where asset = ? and account = ?";
    private final static String SQL_INSERT_ASSET_HISTORY = "insert into asset_history " +
            "(asset, account, last_value, update_value, current_value, comment, time) values (?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_ASSET_HISTORY = "select * from asset_history " +
            "where time >= ? and time < ? order by time desc limit ?";

    // account asset base value 管理

    /**
     * 第一条初始记录值，插入表内
     * @param value asset base value
     * @throws StoreException 存储失败
     */
    public void storeAssetBaseValue(AssetBaseValue value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_ASSET_BASE,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, value.getAsset());
            statement.setString(2, value.getAccount());
            statement.setDouble(3, value.getBaseValue());
            statement.setBoolean(4, value.isEnabled());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                value.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("insert asset base value failed", e);
        }
    }

    public List<AssetBaseValue> queryAssetBaseValues() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_ASSET_BASE)) {
            ResultSet resultSet = statement.executeQuery();
            List<AssetBaseValue> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(new AssetBaseValue(
                        resultSet.getInt("id"),
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("value"),
                        resultSet.getBoolean("enabled")));
            }
            return result;
        } catch (Exception e) {
            throw new StoreException("query asset base failed", e);
        }
    }

    public AssetBaseValue queryAssetBaseValue(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_ASSET_BASE_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            AssetBaseValue result = null;
            while (resultSet.next()) {
                result = new AssetBaseValue(
                        resultSet.getInt("id"),
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("value"),
                        resultSet.getBoolean("enabled"));
            }
            return result;
        } catch (Exception e) {
            throw new StoreException("query asset base failed", e);
        }
    }

    /**
     * 更新初始余额配置，保留使用，应当使用`storyAccountHistoryBaseValue`更新
     */
//    public void updateAssetBaseValue(AssetBaseValue value) throws StoreException {
//
//    }

    public void deleteAssetBaseValue(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_ASSET_BASE)) {
            statement.setInt(1, id);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("delete asset base failed", e);
        }
    }

    public void updateAssetBaseValueEnabled(int id, boolean enabled) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_ASSET_BASE_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("update balance info enabled failed", e);
        }
    }

    public void storeAssetHistoryBaseValue(AssetHistoryBaseValue historyBaseValue) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stat1 = conn.prepareStatement(SQL_INSERT_ASSET_HISTORY,
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement stat2 = conn.prepareStatement(SQL_UPDATE_ASSET_BASE)) {
                stat1.setString(1, historyBaseValue.getAsset());
                stat1.setString(2, historyBaseValue.getAccount());
                stat1.setDouble(3, historyBaseValue.getLastValue());
                stat1.setDouble(4, historyBaseValue.getUpdateValue());
                stat1.setDouble(5, historyBaseValue.getCurrentValue());
                stat1.setString(6, historyBaseValue.getComment());
                if (historyBaseValue.getTimestamp() == 0) {
                    historyBaseValue.setTimestamp(System.currentTimeMillis());
                }
                stat1.setTimestamp(7, new Timestamp(historyBaseValue.getTimestamp()));
                stat1.execute();
                ResultSet resultSet = stat1.getGeneratedKeys();
                int id = 0;
                while (resultSet.next()) {
                    id = resultSet.getInt(1);
                }
                stat2.setDouble(1, historyBaseValue.getCurrentValue());
                stat2.setString(2, historyBaseValue.getAsset());
                stat2.setString(3, historyBaseValue.getAccount());
                int count = stat2.executeUpdate();
                if (count != 1) {
                    throw new SQLException("no matched asset base value.");
                }
                historyBaseValue.setId(id);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StoreException("update asset history value failed.", e);
        }
    }

    public List<AssetHistoryBaseValue> queryAssetHistoryBaseValue(
            long start, long stop, int limit) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_ASSET_HISTORY)) {
            statement.setTimestamp(1, new Timestamp(start));
            statement.setTimestamp(2, new Timestamp(stop));
            statement.setInt(3, limit);
            ResultSet resultSet = statement.executeQuery();
            List<AssetHistoryBaseValue> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(new AssetHistoryBaseValue(
                        resultSet.getInt("id"),
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("last_value"),
                        resultSet.getDouble("update_value"),
                        resultSet.getDouble("current_value"),
                        resultSet.getString("comment"),
                        resultSet.getTimestamp("time").getTime()));
            }
            return values;
        } catch (SQLException e) {
            throw new StoreException("update balance history and info failed.", e);
        }
    }

    // 对账数据，checking balance，资产快照存储
    private static final String SQL_INSERT_ACCOUNT_BALANCE = "insert into checking_balance " +
            "(asset, account, size, price, result, time) values (?, ?, ?, ?, ?, ?)";

    private static final String SQL_QUERY_ACCOUNT_BALANCE = "select * from checking_balance " +
            "where date_trunc('minute', time) = ?";

    public void storeAccountBalance(AccountBalance value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_ACCOUNT_BALANCE)) {
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

    public List<AccountBalance> queryAccountBalance(long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_ACCOUNT_BALANCE)) {
            statement.setTimestamp(1, new Timestamp(time));
            ResultSet resultSet = statement.executeQuery();
            List<AccountBalance> accountBalanceValues = new ArrayList<>();
            while (resultSet.next()) {
                accountBalanceValues.add(new AccountBalance(
                        resultSet.getString("asset"),
                        resultSet.getString("account"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("result"),
                        resultSet.getTimestamp("time").getTime()));
            }
            return accountBalanceValues;
        } catch (SQLException e) {
            throw new StoreException("failed to query account balance at time " + time, e);
        }
    }

}
