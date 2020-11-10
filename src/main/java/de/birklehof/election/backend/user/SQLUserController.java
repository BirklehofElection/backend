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
package de.birklehof.election.backend.user;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.birklehof.election.backend.sql.DefaultMySQLController;
import de.birklehof.election.backend.sql.MySQLController;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SQLUserController implements UserController {

    private final MySQLController sqlController;
    private final LoadingCache<String, Boolean> voteCache = CacheBuilder.newBuilder()
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .concurrencyLevel(4)
        .ticker(Ticker.systemTicker())
        .build(new CacheLoader<>() {
            @Override
            public Boolean load(@NotNull String userId) {
                return SQLUserController.this.sqlController.executeQuery(
                    "SELECT `voted` FROM `users` WHERE `userId` = ?",
                    statement -> statement.setString(1, userId),
                    resultSet -> resultSet.next() && resultSet.getBoolean("voted"),
                    Boolean.FALSE
                );
            }
        });

    @Autowired
    public SQLUserController(DefaultMySQLController sqlController) {
        this.sqlController = sqlController;
        this.sqlController.executeUpdate(
            "CREATE TABLE IF NOT EXISTS `users` (`userId` VARCHAR(50) PRIMARY KEY, `voted` BOOL)",
            statement -> {
            }
        );
    }

    @Override
    public boolean hasVoted(@NotNull String userId) {
        return this.voteCache.getUnchecked(userId);
    }

    @Override
    public void setHasVoted(@NotNull String userId) {
        this.voteCache.put(userId, Boolean.TRUE);
        this.sqlController.executeUpdate(
            "INSERT INTO `users` (`userId`, `voted`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `userId` = ?, `voted` = ?",
            statement -> {
                statement.setString(1, userId);
                statement.setBoolean(2, Boolean.TRUE);
                statement.setString(3, userId);
                statement.setBoolean(4, Boolean.TRUE);
            }
        );
    }
}
