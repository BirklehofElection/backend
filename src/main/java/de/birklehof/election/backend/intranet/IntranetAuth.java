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
package de.birklehof.election.backend.intranet;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.Collection;

public final class IntranetAuth {

    private static final String MAIN_INTRANET_PAGE = "https://intranet.birklehof.de";
    private static final String WEBTOP_INTRANET_PAGE = MAIN_INTRANET_PAGE + "/vdesk/webtop.eui?webtop=";
    // static responses
    private static final AuthResponse SUCCESS = new AuthResponse(HttpStatus.PERMANENT_REDIRECT.value(), "Location: ");
    private static final AuthResponse FAILURE = new AuthResponse(HttpStatus.PERMANENT_REDIRECT.value(), "Location: ");
    private static final AuthResponse INVALID_CREDENTIALS = new AuthResponse(HttpStatus.FORBIDDEN.value(), "Invalid credentials");

    private IntranetAuth() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public static AuthResponse tryLogin(@NotNull String userName, @NotNull String password) {
        try (var client = new WebClient(BrowserVersion.FIREFOX)) {
            client.getOptions().setThrowExceptionOnScriptError(false);
            HtmlPage page = client.getPage(MAIN_INTRANET_PAGE);
            if (page.getWebResponse().getStatusCode() == HttpStatus.OK.value()) {
                var element = findLoginRedirectElement(page.getTabbableElements());
                if (element != null) {
                    HtmlPage loginPage = element.click();
                    if (loginPage.getWebResponse().getStatusCode() == HttpStatus.OK.value()) {
                        HtmlForm form = (HtmlForm) loginPage.getTabbableElements().get(3).getParentNode();
                        form.getInputByName("username").type(userName);
                        form.getInputByName("password").type(password);
                        HtmlPage result = form.getInputByValue("Logon").click();

                        return result.getBaseURI().startsWith(WEBTOP_INTRANET_PAGE) ? SUCCESS : INVALID_CREDENTIALS;
                    } else {
                        return new AuthResponse(loginPage.getWebResponse().getStatusCode(), "unknown error");
                    }
                }
            } else {
                return new AuthResponse(page.getWebResponse().getStatusCode(), "unknown error");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return FAILURE;
    }

    @Nullable
    private static HtmlElement findLoginRedirectElement(@NotNull Collection<HtmlElement> elements) {
        for (HtmlElement element : elements) {
            final DomNode child = element.getFirstChild();
            if (child != null && child.getNodeValue() != null && child.getNodeValue().equals("Pupils / Parents")) {
                return element;
            }
        }
        return null;
    }
}
