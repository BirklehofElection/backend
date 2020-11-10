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

import de.birklehof.election.backend.console.DefaultConsole;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@Component
public class ServerLogger extends Logger {

    private final RecordDispatcher recordDispatcher = new RecordDispatcher(this);

    @Autowired
    public ServerLogger(@NotNull DefaultConsole console) {
        super(ServerLogger.class.getName(), null);
        super.setLevel(Level.ALL);

        try {
            Files.createDirectories(Paths.get("logs"));

            FileHandler fileHandler = new FileHandler("logs/latest.log", 1 << 24, 8, true);
            fileHandler.setLevel(super.getLevel());
            fileHandler.setFormatter(new DefaultFormatter());
            fileHandler.setEncoding(StandardCharsets.UTF_8.name());
            super.addHandler(fileHandler);

            ColouredWriter colouredWriter = new ColouredWriter(console.getLineReader());
            colouredWriter.setLevel(super.getLevel());
            colouredWriter.setFormatter(new DefaultFormatter());
            colouredWriter.setEncoding(StandardCharsets.UTF_8.name());
            super.addHandler(colouredWriter);
        } catch (IOException exception) {
            throw new RuntimeException("Unable to initialize logger", exception);
        }

        System.setOut(new PrintStream(new LoggingOutputStream(this, Level.INFO), true));
        System.setErr(new PrintStream(new LoggingOutputStream(this, Level.SEVERE), true));

        this.recordDispatcher.start();
    }

    @Override
    public void log(LogRecord record) {
        this.recordDispatcher.queue(record);
    }

    public void flushRecord(LogRecord record) {
        super.log(record);
    }

    public void close() throws InterruptedException {
        this.recordDispatcher.interrupt();
        this.recordDispatcher.join();
    }
}
