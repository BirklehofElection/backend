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
package de.birklehof.election.backend.rest;

import com.github.derrop.documents.DefaultDocument;
import com.google.common.hash.Hashing;
import de.birklehof.election.backend.api.ApiController;
import de.birklehof.election.backend.mail.GMailService;
import de.birklehof.election.backend.queued.QueuedTaskExecutor;
import de.birklehof.election.backend.teams.SQLTeamController;
import de.birklehof.election.backend.teams.TeamController;
import de.birklehof.election.backend.user.SQLUserController;
import de.birklehof.election.backend.user.UserController;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1")
@SuppressWarnings("UnstableApiUsage")
public class ApiV1Controller implements ApiController {

    private static final String VOTING_PAGE = "https://birklehofelection.github.io/vote/go.html?token=%s";
    private static final String MESSAGE_BODY = "<html>Hi %s,<br><br>" +
        "im Namen der Kandidaten bedanken wir uns, dass du an der Wahl teilnimmst. Bitte klicke <a href=\"%s\">hier</a>, um abzustimmen.<br><br>" +
        "Mit freundlichen Grüßen<br>" +
        "Charlie und Justus</html>";

    private static final ResponseEntity<String> OK = ResponseEntity.ok(new DefaultDocument("success", true).toJson());
    private static final ResponseEntity<String> UNKNOWN_TEAM = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 2).toJson());
    private static final ResponseEntity<String> ALREADY_SENT = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 4).toJson());
    private static final ResponseEntity<String> INVALID_TOKEN = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 6).toJson());
    private static final ResponseEntity<String> UNABLE_TO_SEND = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 3).toJson());
    private static final ResponseEntity<String> INVALID_EMAIL_ADDRESS = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 5).toJson());
    private static final ResponseEntity<String> ALREADY_VOTED_RESPONSE = ResponseEntity.ok(new DefaultDocument("success", false).append("error", 1).toJson());

    private final UserController userController;
    private final TeamController teamController;

    @Autowired
    public ApiV1Controller(SQLUserController userController, SQLTeamController teamController) {
        this.userController = userController;
        this.teamController = teamController;
    }

    @Override
    @PostMapping("/requestToken")
    public @NotNull ResponseEntity<String> handleTokenRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @RequestHeader @NotNull String inEmail) {
        final var email = inEmail.toLowerCase();
        if (validateEmailAddress(email)) {
            return this.userController.generateToken(Hashing.sha256().hashString(email, StandardCharsets.UTF_8).toString()).map(token -> {
                final var text = String.format(MESSAGE_BODY, parseFirstNameFromEmail(inEmail), String.format(VOTING_PAGE, token));
                if (GMailService.sendMessage(email, "Election Verification", text)) {
                    return OK;
                } else {
                    return UNABLE_TO_SEND;
                }
            }).orElse(ALREADY_SENT);
        } else {
            return INVALID_EMAIL_ADDRESS;
        }
    }

    @Override
    @PostMapping("/vote")
    public @NotNull ResponseEntity<String> vote(@NotNull HttpServletRequest request, @NotNull @RequestHeader String token, @NotNull @RequestHeader String votedTeam) {
        final CompletableFuture<ResponseEntity<String>> future = new CompletableFuture<>();
        QueuedTaskExecutor.queue(() -> future.complete(this.userController.getUserIdOfToken(token).map(userId -> {
            if (this.userController.hasVoted(token)) {
                return ALREADY_VOTED_RESPONSE;
            } else {
                return this.teamController.getTeamByName(votedTeam).map(team -> {
                    this.userController.setHasVoted(token);
                    team.increaseVotes();
                    return OK;
                }).orElse(UNKNOWN_TEAM);
            }
        }).orElse(INVALID_TOKEN)));
        return future.join();
    }

    @Override
    @PostMapping("/validate")
    public @NotNull ResponseEntity<String> validateToken(@NotNull HttpServletRequest request, @NotNull @RequestHeader String token) {
        return ResponseEntity.ok(new DefaultDocument("status", this.userController.validateToken(token).ordinal()).toJson());
    }

    private static boolean validateEmailAddress(@NotNull String email) {
        final var checkedEmail = email.toLowerCase();
        if (!checkedEmail.endsWith("@s.birklehof.de")) {
            return false;
        }

        var parts = checkedEmail.split("\\.");
        if (parts.length < 4) {
            return false;
        }

        return !parts[0].isBlank() && !parts[1].isBlank();
    }

    private static String parseFirstNameFromEmail(@NotNull String emailAddress) {
        final var firstName = emailAddress.split("\\.")[0].toLowerCase();
        final var firstDigit = firstName.charAt(0);
        return firstName.replaceFirst(Character.toString(firstDigit), Character.toString(Character.toUpperCase(firstDigit)));
    }
}
