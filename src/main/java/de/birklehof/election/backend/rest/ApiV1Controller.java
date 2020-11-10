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

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import de.birklehof.election.backend.api.ApiController;
import de.birklehof.election.backend.intranet.AuthResponse;
import de.birklehof.election.backend.intranet.IntranetAuth;
import de.birklehof.election.backend.teams.SQLTeamController;
import de.birklehof.election.backend.teams.Team;
import de.birklehof.election.backend.teams.TeamController;
import de.birklehof.election.backend.user.SQLUserController;
import de.birklehof.election.backend.user.UserController;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@CrossOrigin
@RestController
@RequestMapping("/api/v1")
@SuppressWarnings("UnstableApiUsage")
public class ApiV1Controller implements ApiController {

    private static final String MAIN_PAGE = "";
    private static final String VOTING_PAGE = MAIN_PAGE + "";

    private final UserController userController;
    private final TeamController teamController;
    private final Map<String, CompletableFuture<AuthResponse>> runningLoginSessions = Maps.newConcurrentMap();
    private final Cache<String, Boolean> loggedInUsers = CacheBuilder.newBuilder()
        .concurrencyLevel(4)
        .ticker(Ticker.systemTicker())
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    @Autowired
    public ApiV1Controller(SQLUserController userController, SQLTeamController teamController) {
        this.userController = userController;
        this.teamController = teamController;
    }

    @NotNull
    @Override
    @PostMapping("/login")
    public RedirectView handleLogin(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull @RequestHeader String username, @NotNull @RequestHeader String password) {
        AuthResponse authResponse;
        if (this.runningLoginSessions.containsKey(request.getSession().getId())) {
            authResponse = this.runningLoginSessions.get(request.getSession().getId()).join();
        } else {
            // Register a future for users without any time
            this.runningLoginSessions.put(request.getSession().getId(), new CompletableFuture<>());
            // now try the actual login to the intranet
            authResponse = IntranetAuth.tryLogin(username, password);
        }

        RedirectView result;
        if (authResponse.getCode() == HttpStatus.MOVED_PERMANENTLY.value()) {
            if (authResponse.isSuccess()) {
                var usernameHash = Hashing.sha256().hashString(username, StandardCharsets.UTF_8).toString();
                this.loggedInUsers.put(usernameHash, Boolean.TRUE);
                request.getSession().setAttribute("user", usernameHash);
            }
            result = new RedirectView(VOTING_PAGE);
        } else {
            result = new RedirectView(MAIN_PAGE + "?error=3");
        }

        var future = this.runningLoginSessions.remove(request.getSession().getId());
        if (future != null) {
            future.complete(authResponse);
        }

        return result;
    }

    @Override
    @PostMapping("/vote/{team}")
    public @NotNull RedirectView vote(@NotNull HttpServletRequest request, @NotNull @PathVariable(required = false) String votedTeam) {
        final var userIdObject = request.getSession().getAttribute("user");
        if (!(userIdObject instanceof String) || this.loggedInUsers.getIfPresent(userIdObject) == null) {
            request.getSession().invalidate();
            return new RedirectView(MAIN_PAGE + "?error=3");
        }

        final var userId = (String) userIdObject;
        if (this.userController.hasVoted(userId)) {
            return new RedirectView(MAIN_PAGE + "?error=1");
        }

        final var team = this.teamController.getTeamByName(votedTeam);
        if (team.isPresent()) {
            this.userController.setHasVoted(userId);
            team.get().increaseVotes();
            return new RedirectView(VOTING_PAGE + "?success=1");
        } else {
            return new RedirectView(MAIN_PAGE + "?error=2");
        }
    }

    @Override
    @GetMapping("/votes/{team}")
    public int getVotes(@NotNull @PathVariable String team) {
        return this.teamController.getTeamByName(team).map(Team::getVotes).orElse(-1);
    }
}
