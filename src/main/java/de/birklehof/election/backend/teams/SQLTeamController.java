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

import com.google.common.collect.Maps;
import de.birklehof.election.backend.sql.DefaultMySQLController;
import de.birklehof.election.backend.sql.MySQLController;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Component
public class SQLTeamController implements TeamController {

    private final MySQLController sqlController;
    private final Map<String, Team> loadedTeams;

    @Autowired
    public SQLTeamController(DefaultMySQLController sqlController) {
        this.sqlController = sqlController;
        this.sqlController.executeUpdate(
            "CREATE TABLE IF NOT EXISTS `teams` (`name` VARCHAR(50) PRIMARY KEY, `votes` INT)",
            statement -> {
            }
        );
        // get all registered teams
        this.loadedTeams = this.sqlController.executeQuery(
            "SELECT * FROM `teams`",
            statement -> {
            }, resultSet -> {
                Map<String, Team> teams = Maps.newConcurrentMap();
                while (resultSet.next()) {
                    final var name = resultSet.getString("name");
                    teams.put(name, new SQLTeam(name, resultSet.getInt("votes"), sqlController));
                }
                return teams;
            }, Maps.newConcurrentMap()
        );
    }

    @Override
    public @NotNull Optional<Team> getTeamByName(@NotNull String teamName) {
        return Optional.ofNullable(this.loadedTeams.get(teamName));
    }

    @Override
    public @NotNull Team registerTeam(@NotNull String name) {
        var team = this.loadedTeams.get(name);
        if (team != null) {
            return team;
        }

        team = new SQLTeam(name, 0, this.sqlController);
        this.sqlController.executeUpdate(
            "INSERT INTO `teams` (`name`, `votes`) VALUES (?, ?)",
            statement -> {
                statement.setString(1, name);
                statement.setInt(2, 0);
            }
        );
        this.loadedTeams.put(name, team);
        return team;
    }

    @Override
    public void deleteTeam(@NotNull String name) {
        this.sqlController.executeUpdate(
            "DELETE FROM `teams` WHERE `name` = ?",
            statement -> statement.setString(1, name)
        );
        this.loadedTeams.remove(name);
    }

    @Override
    public @NotNull Collection<Team> getRegisteredTeams() {
        return this.loadedTeams.values();
    }
}
