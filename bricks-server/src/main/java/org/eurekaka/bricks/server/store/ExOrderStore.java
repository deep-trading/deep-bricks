package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.TradeNotification;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderType;
import org.eurekaka.bricks.common.model.PlanOrder;
import org.eurekaka.bricks.server.model.ExOrder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExOrderStore {

    private static final String SQL_INSERT_HEDGING_ORDER = "insert into hedging_order " +
            "(name, symbol, account, side, order_type, quantity, size, price, last_price, plan_id) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_HEDGING_ORDER_COMMITTED = "update hedging_order " +
            "set committed = true, order_id = ? where id = ?";

    private static final String SQL_QUERY_HEDGING_ORDER_UNCOMMITTED = "select * from hedging_order " +
            "where committed = false";

    public void storeExOrder(ExOrder order) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     SQL_INSERT_HEDGING_ORDER, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, order.getName());
            statement.setString(2, order.getSymbol());
            statement.setString(3, order.getAccount());
            statement.setString(4, order.getSide().name());
            statement.setString(5, order.getOrderType().name());
            statement.setLong(6, order.getQuantity());
            statement.setDouble(7, order.getSize());
            statement.setDouble(8, order.getPrice());
            statement.setDouble(9, order.getLastPrice());
            statement.setLong(10, order.getPlanId());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                long id = resultSet.getLong(1);
                order.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("failed to insert order v2 value: " + order, e);
        }
    }

    public void commitExOrder(String orderId, long id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_HEDGING_ORDER_COMMITTED)) {
            statement.setString(1, orderId);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to update uncommitted order v2, id = " + id, e);
        }
    }

    public List<ExOrder> queryUncommittedOrders() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_HEDGING_ORDER_UNCOMMITTED)) {
            ResultSet resultSet = statement.executeQuery();
            List<ExOrder> orders = new ArrayList<>();
            while (resultSet.next()) {
                ExOrder order = new ExOrder(
                        resultSet.getString("account"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        OrderSide.valueOf(resultSet.getString("side")),
                        OrderType.valueOf(resultSet.getString("order_type")),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("quantity"),
                        resultSet.getDouble("last_price"),
                        resultSet.getLong("plan_id"));
                order.setId(resultSet.getLong("id"));
                orders.add(order);
            }
            return orders;
        } catch (SQLException e) {
            throw new StoreException("failed to query uncommitted orders", e);
        }
    }

    private static final String SQL_INSERT_HEDGING_PLAN_ORDER = "insert into hedging_plan_order " +
            "(name, quantity, symbol_price, left_quantity, start_time, update_time) " +
            "values (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_HEDGING_PLAN_ORDER_LEFT_QUANTITY = "update hedging_plan_order " +
            "set left_quantity = ?, update_time = ? where id = ?";

    private static final String SQL_UPDATE_HEDGING_PLAN_ORDER_START_TIME = "update hedging_plan_order " +
            "set start_time = ? where id = ?";

    private static final String SQL_QUERY_HEDGING_PLAN_ORDER_NOT_FINISHED = "select * from hedging_plan_order " +
            "where left_quantity != 0";

    public void storePlanOrder(PlanOrder order) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     SQL_INSERT_HEDGING_PLAN_ORDER, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, order.getName());
            statement.setLong(2, order.getQuantity());
            statement.setLong(3, order.getSymbolPrice());
            statement.setLong(4, order.getLeftQuantity());
            statement.setTimestamp(5, new Timestamp(order.getStartTime()));
            statement.setTimestamp(6, new Timestamp(order.getUpdateTime()));
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                long id = resultSet.getLong(1);
                order.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("failed to insert plan order value: " + order, e);
        }
    }

    public void updatePlanOrderLeftQuantity(long leftQuantity, long updateTime, long id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_HEDGING_PLAN_ORDER_LEFT_QUANTITY)) {
            statement.setLong(1, leftQuantity);
            statement.setTimestamp(2, new Timestamp(updateTime));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to update plan order left quantity, id = " + id, e);
        }
    }

    public void updatePlanOrderStartTime(long startTime, long id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_HEDGING_PLAN_ORDER_START_TIME)) {
            statement.setTimestamp(1, new Timestamp(startTime));
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to update plan order start time, id = " + id, e);
        }
    }

    public List<PlanOrder> queryPlanOrderNotFinished() throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_QUERY_HEDGING_PLAN_ORDER_NOT_FINISHED)) {
            ResultSet resultSet = statement.executeQuery();
            List<PlanOrder> orders = new ArrayList<>();
            while (resultSet.next()) {
                PlanOrder order = new PlanOrder(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getLong("quantity"),
                        resultSet.getLong("symbol_price"),
                        resultSet.getLong("left_quantity"),
                        resultSet.getTimestamp("start_time").getTime(),
                        resultSet.getTimestamp("update_time").getTime());
                orders.add(order);
            }
            return orders;
        } catch (SQLException e) {
            throw new StoreException("failed to query uncommitted plan orders", e);
        }
    }


    private static final String SQL_INSERT_HISTORY_ORDER = "insert into history_order (" +
            "fill_id, order_id, name, symbol, account, side, type, price, size, result, fee_asset, fee, time) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_HISTORY_ORDER = "select * from history_order " +
            "where time >= ? and time <= ? ";

    public void storeHistoryOrder(TradeNotification order) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_HISTORY_ORDER,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, order.getFillId());
            statement.setString(2, order.getOrderId());
            statement.setString(3, order.getName());
            statement.setString(4, order.getSymbol());
            statement.setString(5, order.getAccount());
            statement.setString(6, order.getSide().name());
            statement.setString(7, order.getType().name());
            statement.setDouble(8, order.getPrice());
            statement.setDouble(9, order.getSize());
            statement.setDouble(10, order.getResult());
            statement.setString(11, order.getFeeAsset());
            statement.setDouble(12, order.getFee());
            statement.setTimestamp(13, new Timestamp(order.getTime()));
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                order.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("failed to insert history order value: " + order, e);
        }
    }

    public List<TradeNotification> queryHistoryOrders(String account, String name,
                                                      long start, long stop, int limit) throws StoreException {
        String querySql = SQL_QUERY_HISTORY_ORDER;
        if (account != null) {
            querySql += " and account = ?";
        }
        if (name != null) {
            querySql += " and name = ?";
        }
        querySql += " order by time desc limit ?";

        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(querySql)) {
            statement.setTimestamp(1, new Timestamp(start));
            statement.setTimestamp(2, new Timestamp(stop));
            int index = 3;
            if (account != null) {
                statement.setString(index++, account);
            }
            if (name != null) {
                statement.setString(index++, name);
            }
            statement.setInt(index, limit);

            ResultSet resultSet = statement.executeQuery();
            List<TradeNotification> trades = new ArrayList<>();
            while (resultSet.next()) {
                TradeNotification order = new TradeNotification(
                        resultSet.getString("fill_id"),
                        resultSet.getString("order_id"),
                        resultSet.getString("account"),
                        resultSet.getString("name"),
                        resultSet.getString("symbol"),
                        OrderSide.valueOf(resultSet.getString("side")),
                        OrderType.valueOf(resultSet.getString("type")),
                        resultSet.getDouble("price"),
                        resultSet.getDouble("size"),
                        resultSet.getDouble("result"),
                        resultSet.getString("fee_asset"),
                        resultSet.getDouble("fee"),
                        resultSet.getTimestamp("time").getTime());
                trades.add(order);
            }
            return trades;
        } catch (SQLException e) {
            throw new StoreException("failed to query history trades", e);
        }
    }


    private static final String SQL_INSERT_ORDER_RESULT = "insert into hedging_order_result " +
            "(order_id, left_size, status) values (?, ?, ?)";

    public void storeOrderResult(String orderId, double leftSize, String status) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_INSERT_ORDER_RESULT)) {
            statement.setString(1, orderId);
            statement.setDouble(2, leftSize);
            statement.setString(3, status);
            statement.execute();
        } catch (SQLException e) {
            throw new StoreException("failed to insert order result: " + orderId, e);
        }
    }
}
