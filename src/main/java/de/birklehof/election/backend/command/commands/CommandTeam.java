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
package de.birklehof.election.backend.command.commands;

import de.birklehof.election.backend.command.Command;
import de.birklehof.election.backend.command.CommandManager;
import de.birklehof.election.backend.command.CommandSender;
import de.birklehof.election.backend.teams.SQLTeamController;
import de.birklehof.election.backend.teams.Team;
import de.birklehof.election.backend.teams.TeamController;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommandTeam implements Command {

    private final TeamController teamController;

    @Autowired
    public CommandTeam(CommandManager commandManager, SQLTeamController teamController) {
        commandManager.registerCommand(this, "Management of teams", "teams", "team", "t");
        this.teamController = teamController;
    }

    @Override
    public void process(@NotNull CommandSender sender, @NonNls String[] args, @NotNull String commandLine) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            for (Team registeredTeam : this.teamController.getRegisteredTeams()) {
                sender.sendMessage("Team: " + registeredTeam.getName() + "; Votes: " + registeredTeam.getVotes());
            }
            return;
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "create":
                    final var team = this.teamController.registerTeam(args[1]);
                    sender.sendMessage("Team " + team.getName() + " was registered successfully");
                    return;
                case "delete":
                    this.teamController.deleteTeam(args[1]);
                    sender.sendMessage("Team " + args[1] + " was deleted successfully");
                    return;
                case "votes":
                    this.teamController.getTeamByName(args[1]).ifPresentOrElse(
                        t -> sender.sendMessage("Team " + t.getName() + " has " + t.getVotes() + " votes"),
                        () -> sender.sendMessage("Team " + args[1] + " is unknown")
                    );
                    return;
                default:
                    break;
            }
        }

        showHelp(sender);
    }

    private static void showHelp(CommandSender sender) {
        sender.sendMessage("teams list");
        sender.sendMessage("teams create <name>");
        sender.sendMessage("teams delete <name>");
        sender.sendMessage("teams votes <name>");
    }
}
