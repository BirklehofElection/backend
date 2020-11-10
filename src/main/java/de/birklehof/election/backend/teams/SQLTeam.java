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
package de.birklehof.election.backend.teams;

import de.birklehof.election.backend.sql.MySQLController;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public class SQLTeam implements Team {

    private final String name;
    private final MySQLController sqlController;
    private final AtomicInteger votes = new AtomicInteger();

    protected SQLTeam(String name, int votes, MySQLController sqlController) {
        this.name = name;
        this.sqlController = sqlController;
        this.votes.set(votes);
    }

    @Override
    public void increaseVotes() {
        int newVotes = this.votes.incrementAndGet();
        this.sqlController.executeUpdate(
            "UPDATE `teams` SET `votes` = ? WHERE `name` = ?",
            statement -> {
                statement.setInt(1, newVotes);
                statement.setString(2, this.name);
            }
        );
    }

    @Override
    public int getVotes() {
        return this.votes.get();
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public int compareTo(@NotNull Team o) {
        return Integer.compare(this.votes.get(), o.getVotes());
    }
}
