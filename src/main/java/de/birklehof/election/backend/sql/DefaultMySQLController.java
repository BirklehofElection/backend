/*
 * This file is part of election-backend, licensed under the MIT License (MIT).
 *
 * Copyright (c) Pasqual Koschmieder <https://github.com/derklaro>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package de.birklehof.election.backend.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.birklehof.election.backend.config.ServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class DefaultMySQLController implements MySQLController {

    private final HikariDataSource dataSource;

    @Autowired
    public DefaultMySQLController(ServerConfiguration configuration) {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(String.format(
            "jdbc:mysql://%s:%d/%s?serverTimezone=UTC",
            configuration.getMysqlHost(),
            configuration.getMysqlPort(),
            configuration.getMysqlDatabase()
        ));
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setUsername(configuration.getMysqlUser());
        hikariConfig.setPassword(configuration.getMysqlPassword());

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void executeUpdate(@NotNull String statement, @NotNull SQLConsumer modifier) {
        try (var connection = this.dataSource.getConnection(); var preparedStatement = connection.prepareStatement(statement)) {
            modifier.accept(preparedStatement);
            preparedStatement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public <T> T executeQuery(@NotNull String query, @NotNull SQLConsumer modifier, @NotNull SQLFunction<T> mapper, T defaultValue) {
        try (var connection = this.dataSource.getConnection(); var preparedStatement = connection.prepareStatement(query)) {
            modifier.accept(preparedStatement);
            try (var resultSet = preparedStatement.executeQuery()) {
                var result = mapper.apply(resultSet);
                return result == null ? defaultValue : result;
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
            return defaultValue;
        }
    }
}
