package org.eurekaka.bricks.server.store;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.server.model.WarehouseMetaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class WarehouseMetaStore {
    private static Logger logger = LoggerFactory.getLogger(WarehouseMetaStore.class);

    private static final String SQL_INSERT_WAREHOUSE_META = "insert into warehouse_meta " +
            "(table_name, host, time, path) values (?, ?, ?, ?)";
//    private static final String SQL_QUERY_UNCOMMITTED_META = "select * from warehouse_meta where committed = false";
    private static final String SQL_UPDATE_WAREHOUSE_META = "update warehouse_meta set committed = true where id = ?";
    private static final String SQL_DELETE_WAREHOUSE_META = "delete from warehouse_meta " +
            "where host = ? and table_name = ? and time = ?";
    private static final String SQL_DELETE_WAREHOUSE_META2 = "delete from warehouse_meta where committed = false";

    public void storeWarehouseMeta(WarehouseMetaValue value) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(
                     SQL_INSERT_WAREHOUSE_META, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, value.getTableName());
            statement.setString(2, value.getHost());
            statement.setTimestamp(3, new Timestamp(value.getTime()));
            statement.setString(4, value.getPath());
            statement.execute();
            ResultSet resultSet = statement.getGeneratedKeys();
            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                value.setId(id);
            }
        } catch (SQLException e) {
            throw new StoreException("failed to insert warehouse meta value: " + value, e);
        }
    }

    public void commitWarehouseMeta(int id) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_UPDATE_WAREHOUSE_META)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to update uncommitted warehouse meta, id = " + id, e);
        }
    }

    public void deleteWarehouseMeta(String host, String tableName, long time) throws StoreException {
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement statement = conn.prepareStatement(SQL_DELETE_WAREHOUSE_META)) {
            statement.setString(1, host);
            statement.setString(2, tableName);
            statement.setTimestamp(3, new Timestamp(time));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to delete warehouse meta, host: "
                    + host + ", table: " + tableName + ", time: " + time, e);
        }
    }


    public void importCSV(BufferedReader reader, String table, long start, long stop) throws StoreException {
        String delete = "delete from " + table
                + " where created_at >= ? and created_at < ?";

        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement delStatement = conn.prepareStatement(delete)) {
            // delete old data
            delStatement.setTimestamp(1, new Timestamp(start));
            delStatement.setTimestamp(2, new Timestamp(stop));
            int count = delStatement.executeUpdate();
            logger.debug("{} rows deleted from table {}", count, table);

            ResultSet rs = conn.getMetaData().getColumns(null, null, table, null);

            try (CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
                Map<String, Integer> headerMap = csvParser.getHeaderMap();
                LinkedHashMap<String, Integer> colNameToType = new LinkedHashMap<>();
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME").toUpperCase();
                    Integer dataType = rs.getInt("DATA_TYPE");
//                if ("id".equalsIgnoreCase(colName)) continue;
                    if (headerMap.containsKey(colName)) {
                        colNameToType.put(colName, dataType);
                    }
                }

                String columns = String.join(",", colNameToType.keySet());
                String placeholders = String.join(",", Collections.nCopies(colNameToType.size(), "?"));
                String insSql = "insert into " + table + " (" + columns + ") values (" + placeholders + ")";

                try (PreparedStatement insStatement = conn.prepareStatement(insSql)) {
                    for (CSVRecord record : csvParser) {
                        insertCSVRecord(record, colNameToType, insStatement);
                    }
                    insStatement.executeBatch();
                }
            }
        } catch (SQLException | IOException e) {
            throw new StoreException("fail to import from CSV to table " + table, e);
        }
    }

    public void exportCSV(String table, long start, long stop) throws StoreException {
        String sql = "select * from " + table + " where created_at >= ? and created_at < ?";

        String filename = "/tmp/" + table + stop + ".csv.gz";
        try (Connection conn = DatabaseStore.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(filename));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gzipOutputStream));
             CSVPrinter csvPrinter = new CSVPrinter(bw, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            stmt.setTimestamp(1, new Timestamp(start));
            stmt.setTimestamp(2, new Timestamp(stop));
            ResultSet rs = stmt.executeQuery();


            appendCSVHeader(rs, csvPrinter);
            while (rs.next()) {
                appendCSVRecord(rs, csvPrinter);
            }
            csvPrinter.flush();
        } catch (SQLException | IOException e) {
            throw new StoreException("export table " + table + " to CSV failed", e);
        }

    }

    private void insertCSVRecord(
            CSVRecord record, LinkedHashMap<String, Integer> colNameToType, PreparedStatement statement)
            throws SQLException, StoreException {
        int i = 1;
        for (Map.Entry<String, Integer> entry : colNameToType.entrySet()) {
            String colName = entry.getKey();
            int colType = entry.getValue();
            if (record.isMapped(colName)) {
                switch (colType) {
                    case Types.TIMESTAMP:
                    case Types.TIMESTAMP_WITH_TIMEZONE:
                        statement.setTimestamp(i, new Timestamp(Long.parseLong(record.get(colName))));
                        break;
                    case Types.VARCHAR:
                        statement.setString(i, record.get(colName));
                        break;
                    case Types.BIGINT:
                        statement.setLong(i, Long.parseLong(record.get(colName)));
                        break;
                    case Types.INTEGER:
                        statement.setInt(i, Integer.parseInt(record.get(colName)));
                        break;
                    case Types.DOUBLE:
                    case Types.NUMERIC:
                    case Types.DECIMAL:
                        statement.setDouble(i, Double.parseDouble(record.get(colName)));
                        break;
                    case Types.BOOLEAN:
                    case Types.BIT:
                        statement.setBoolean(i, Boolean.parseBoolean(record.get(colName)));
                        break;
                    default:
                        throw new StoreException("Data type is unsupported by CSV parser: " + colType
                                + ", column name: " + colName);
                }
                i++;
            }
        }
        statement.addBatch();
    }

    private void appendCSVHeader(ResultSet rs, CSVPrinter csvPrinter) throws SQLException, IOException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        List<String> colNames = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            colNames.add(rsmd.getColumnName(i).toUpperCase());
        }
        csvPrinter.printRecord(colNames);
    }

    private void appendCSVRecord(ResultSet rs, CSVPrinter csvPrinter) throws SQLException, IOException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int colCount = rsmd.getColumnCount();
        List<Object> record = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            int colType = rsmd.getColumnType(i);
            switch (colType) {
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    record.add(rs.getTimestamp(i).getTime());
                    break;
                default:
                    record.add(rs.getObject(i));
            }
        }
        csvPrinter.printRecord(record);
    }

}
