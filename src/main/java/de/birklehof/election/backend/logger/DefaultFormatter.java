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
package de.birklehof.election.backend.logger;

import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class DefaultFormatter extends Formatter {

    private static final DateFormat FORMAT = new SimpleDateFormat(System.getProperty("log-date-format", "dd.MM.yyyy kk:mm:ss"));

    @Override
    public String format(LogRecord record) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(FORMAT.format(record.getMillis()));
        stringBuilder.append(" [");
        this.appendLevel(stringBuilder, record.getLevel());
        stringBuilder.append("] ");
        stringBuilder.append(ConsoleColour.stripColor('&', super.formatMessage(record)));
        stringBuilder.append('\n');

        if (record.getThrown() != null) {
            StringWriter stringWriter = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(stringWriter));
            stringBuilder.append(stringWriter);
        }

        return stringBuilder.toString();
    }

    private void appendLevel(@NotNull StringBuilder stringBuilder, @NotNull Level level) {
        ConsoleColour consoleColour;
        if (level == Level.INFO) {
            consoleColour = ConsoleColour.GREEN;
        } else if (level == Level.WARNING) {
            consoleColour = ConsoleColour.YELLOW;
        } else if (level == Level.SEVERE) {
            consoleColour = ConsoleColour.RED;
        } else {
            consoleColour = ConsoleColour.AQUA;
        }

        stringBuilder.append(consoleColour).append(level.getLocalizedName()).append(ConsoleColour.RESET);
    }
}
