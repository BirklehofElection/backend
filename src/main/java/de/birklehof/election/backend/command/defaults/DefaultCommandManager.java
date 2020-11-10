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
package de.birklehof.election.backend.command.defaults;

import com.google.common.collect.Lists;
import de.birklehof.election.backend.command.Command;
import de.birklehof.election.backend.command.CommandContainer;
import de.birklehof.election.backend.command.CommandManager;
import de.birklehof.election.backend.command.CommandSender;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class DefaultCommandManager implements CommandManager {

    private final Collection<CommandContainer> commands = Lists.newCopyOnWriteArrayList();

    @Override
    public @NotNull DefaultCommandManager registerCommand(@NotNull Command command, @NotNull String description, @NonNls String... names) {
        for (String alias : names) {
            Optional<CommandContainer> registeredCommand = this.getCommand(alias.toLowerCase());
            if (registeredCommand.isPresent()) {
                throw new RuntimeException("Command " + registeredCommand.get().getCommand().getClass().getName() + " clashes with "
                    + command.getClass().getName() + " because of alias '" + alias + "'");
            }
        }

        this.commands.add(new CommandContainer(Arrays.asList(names), description, command));
        return this;
    }

    @Override
    public void unregisterCommand(@NotNull CommandContainer command) {
        this.commands.removeIf(commandContainer -> {
            for (String alias : commandContainer.getAliases()) {
                if (command.getAliases().stream().anyMatch(alias::equals)) {
                    return true;
                }
            }

            return false;
        });
    }

    @NotNull
    @Override
    public Optional<CommandContainer> getCommand(@NotNull String anyAlias) {
        for (CommandContainer command : this.commands) {
            if (command.getAliases().contains(anyAlias.toLowerCase())) {
                return Optional.of(command);
            }
        }

        return Optional.empty();
    }

    @NotNull
    @Override
    public @UnmodifiableView Collection<CommandContainer> getCommands() {
        return Collections.unmodifiableCollection(this.commands);
    }

    @Override
    public boolean process(@NotNull String commandLine, @NotNull CommandSender commandSender) {
        return this.process(commandLine, null, commandSender);
    }

    @Override
    public boolean process(@NotNull String commandLine, Predicate<CommandContainer> allowedTester, @NotNull CommandSender commandSender) {
        String[] split = commandLine.split(" ");
        CommandContainer command = this.getCommand(split);
        if (command == null) {
            return false;
        }

        if (allowedTester != null && !allowedTester.test(command)) {
            return false;
        }

        String[] args = split.length > 1 ? Arrays.copyOfRange(split, 1, split.length) : new String[0];
        try {
            command.getCommand().process(commandSender, args, commandLine);
        } catch (Throwable throwable) {
            System.err.println("Exception handling command \"" + split[0] + "\" with arguments " + String.join(", ", args));
            throwable.printStackTrace();
        }

        return true;
    }

    private @Nullable CommandContainer getCommand(@NotNull String[] split) {
        if (split.length == 0) {
            return null;
        }

        return this.getCommand(split[0]).orElse(null);
    }
}
