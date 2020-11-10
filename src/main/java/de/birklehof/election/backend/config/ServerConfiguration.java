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
package de.birklehof.election.backend.config;

import com.github.derrop.documents.Documents;
import de.birklehof.election.backend.reflection.ReflectionUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ServerConfiguration {

    private static final Path CONFIGURATION_FILE = Path.of("config.json");
    private static boolean initialized;
    // web server
    private final String webServerHost;
    private final int webServerPort;
    // sql
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUser;
    private final String mysqlPassword;

    public ServerConfiguration() {
        this.webServerHost = "127.0.0.1";
        this.webServerPort = 8080;
        this.mysqlHost = "127.0.0.1";
        this.mysqlPort = 3306;
        this.mysqlDatabase = "election";
        this.mysqlUser = "root";
        this.mysqlPassword = "password";
        this.load();
    }

    public void load() {
        if (Files.notExists(CONFIGURATION_FILE)) {
            Documents.newDocument(this).json().write(CONFIGURATION_FILE);
        }

        if (!initialized) {
            initialized = true;
            ReflectionUtils.copyAllFields(this, Documents.jsonStorage().read(CONFIGURATION_FILE).toInstanceOf(ServerConfiguration.class));
        }
    }

    public String getWebServerHost() {
        return this.webServerHost;
    }

    public int getWebServerPort() {
        return this.webServerPort;
    }

    public String getMysqlHost() {
        return this.mysqlHost;
    }

    public int getMysqlPort() {
        return this.mysqlPort;
    }

    public String getMysqlDatabase() {
        return this.mysqlDatabase;
    }

    public String getMysqlUser() {
        return this.mysqlUser;
    }

    public String getMysqlPassword() {
        return this.mysqlPassword;
    }
}
