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
import com.google.common.hash.Hashing;
import de.birklehof.election.backend.sql.DefaultMySQLController;
import de.birklehof.election.backend.sql.MySQLController;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@SuppressWarnings("UnstableApiUsage")
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
                    "SELECT `voted` FROM `users` WHERE `token` = ?",
                    statement -> statement.setString(1, userId),
                    resultSet -> resultSet.next() && resultSet.getBoolean("voted"),
                    Boolean.FALSE
                );
            }
        });
    private final LoadingCache<String, String> tokenCache = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .concurrencyLevel(4)
        .ticker(Ticker.systemTicker())
        .build(new CacheLoader<>() {
            @Override
            public String load(@NotNull String userId) throws Exception {
                String token = SQLUserController.this.sqlController.executeQuery(
                    "SELECT `token` FROM `users` WHERE `userId` = ?",
                    statement -> statement.setString(1, userId),
                    resultSet -> resultSet.next() ? resultSet.getString("token") : null,
                    null
                );
                if (token != null) {
                    return token;
                } else {
                    throw new ExecutionException("not in db", new NullPointerException());
                }
            }
        });

    @Autowired
    public SQLUserController(DefaultMySQLController sqlController) {
        this.sqlController = sqlController;
        this.sqlController.executeUpdate(
            "CREATE TABLE IF NOT EXISTS `users` (`userId` VARCHAR(255) PRIMARY KEY, `token` VARCHAR(255), `voted` BOOL)",
            statement -> {
            }
        );
    }

    @Override
    public boolean hasVoted(@NotNull String token) {
        return this.voteCache.getUnchecked(Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString());
    }

    @Override
    public void setHasVoted(@NotNull String token) {
        final var hashedToken = Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
        this.voteCache.put(hashedToken, Boolean.TRUE);
        this.sqlController.executeUpdate(
            "UPDATE `users` SET `voted` = ? WHERE `token` = ?",
            statement -> {
                statement.setBoolean(1, Boolean.TRUE);
                statement.setString(2, hashedToken);
            }
        );
    }

    @Override
    public @NotNull Optional<String> getUserIdOfToken(@NotNull String token) {
        try {
            final var hashedToken = Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
            for (var entry : this.tokenCache.asMap().entrySet()) {
                if (entry.getValue().equals(hashedToken)) {
                    return Optional.of(entry.getKey());
                }
            }

            final var userId = this.sqlController.executeQuery(
                "SELECT `userId` FROM `users` WHERE `token` = ?",
                statement -> statement.setString(1, hashedToken),
                resultSet -> resultSet.next() ? resultSet.getString("userId") : null,
                null
            );
            if (userId != null) {
                this.tokenCache.put(userId, token);
            }
            return Optional.ofNullable(userId);
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    @Override
    public @NotNull Optional<String> generateToken(@NotNull String userId) {
        try {
            this.tokenCache.get(userId);
            return Optional.empty();
        } catch (ExecutionException exception) {
            final var token = randomString() + randomString();
            final var hashedToken = Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
            this.sqlController.executeUpdate(
                "INSERT INTO `users` (`userId`, `token`, `voted`) VALUES (?, ?, ?)",
                statement -> {
                    statement.setString(1, userId);
                    statement.setString(2, hashedToken);
                    statement.setBoolean(3, Boolean.FALSE);
                }
            );
            this.tokenCache.put(userId, hashedToken);
            return Optional.of(token);
        }
    }

    @Override
    public @NotNull TokenValidateResult validateToken(@NotNull String token) {
        return this.getUserIdOfToken(token).map(userId -> {
            final var hashedToken = Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
            return this.voteCache.getUnchecked(hashedToken) ? TokenValidateResult.ALREADY_USED : TokenValidateResult.OK;
        }).orElse(TokenValidateResult.INVALID);
    }

    private static String randomString() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
