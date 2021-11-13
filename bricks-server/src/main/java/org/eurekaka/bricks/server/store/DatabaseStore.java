package org.eurekaka.bricks.server.store;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;


/**
 * 使用全局静态数据库连接入口
 */
public class DatabaseStore {
    private static DataSource dataSource;

    private DatabaseStore() {}

    public static void setDataSource(DataSource dataSource) {
        DatabaseStore.dataSource = dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void close() throws IOException {
        if (dataSource instanceof Closeable) {
            ((Closeable) dataSource).close();
        }
    }

    public static void initSql(String filename) throws Exception {
        byte[] contents = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream(filename)).readAllBytes();
        String[] sqls = new String(contents).split(";");
        for (String sql : sqls) {
            String s = sql.trim().toLowerCase();
            if (!s.isEmpty() && !s.startsWith("create index")) {
                // 移除ddl中的foreign key
                StringBuilder strBuilder = new StringBuilder();
                boolean header = true;
                boolean foreign = false;
                for (String part : s.split(",")) {
                    if (header) {
                        strBuilder.append(part);
                        header = false;
                    } else {
                        if (part.trim().startsWith("foreign key")) {
                            foreign = true;
                            continue;
                        }
                        strBuilder.append(",").append(part);
                    }
                }
                if (foreign) {
                    strBuilder.append(");");
                }

                try (Connection conn = DatabaseStore.getConnection();
                     Statement statement = conn.createStatement()){
                    statement.execute(strBuilder.toString());
                }
            }
        }
    }

    public static DataSource getDatabaseSource(Config config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getString("url"));
        hikariConfig.setDriverClassName(config.getString("driver"));
        if (config.hasPath("username")) {
            hikariConfig.setUsername(config.getString("username"));
        }
        if (config.hasPath("password")) {
            hikariConfig.setPassword(config.getString("password"));
        }
        if (config.hasPath("property")) {
            for (Map.Entry<String, ConfigValue> entry : config.getConfig("property").entrySet()) {
                hikariConfig.addDataSourceProperty(entry.getKey(),
                        entry.getValue().unwrapped().toString());
            }
        }

        return new HikariDataSource(hikariConfig);
    }
}
