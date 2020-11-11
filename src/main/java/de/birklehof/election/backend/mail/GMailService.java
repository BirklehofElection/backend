package de.birklehof.election.backend.mail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import org.jetbrains.annotations.NotNull;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

public final class GMailService {

    private static final Gmail GMAIL_SERVICE = initialize();

    private GMailService() {
        throw new UnsupportedOperationException();
    }

    public static boolean sendMessage(@NotNull String to, @NotNull String subject, @NotNull String bodyText) {
        try {
            var message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
            message.setFrom("birklehof.election@gmail.com");
            message.setContent(bodyText, "text/html; charset=utf-8");
            message.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);

            try (var out = new ByteArrayOutputStream()) {
                message.writeTo(out);

                var mailMessage = new Message();
                mailMessage.setRaw(Base64.encodeBase64URLSafeString(out.toByteArray()));

                GMAIL_SERVICE.users().messages().send("me", mailMessage).execute();
                return true;
            }
        } catch (IOException | MessagingException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    @NotNull
    private static Gmail initialize() {
        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            return new Gmail.Builder(
                transport,
                JacksonFactory.getDefaultInstance(),
                getCredentials(transport)
            ).setApplicationName("Birklehof Election").build();
        } catch (GeneralSecurityException | IOException exception) {
            throw new RuntimeException("Unable to authorize with google api", exception);
        }
    }

    @NotNull
    private static Credential getCredentials(@NotNull NetHttpTransport httpTransport) {
        try (var reader = new InputStreamReader(Files.newInputStream(Path.of("credentials.json")))) {
            var secrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), reader);
            var authFlow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JacksonFactory.getDefaultInstance(),
                secrets,
                List.of(GmailScopes.GMAIL_SEND)
            ).setDataStoreFactory(new FileDataStoreFactory(new File("tokens"))).setAccessType("offline").build();
            var receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            return new AuthorizationCodeInstalledApp(authFlow, receiver).authorize("me");
        } catch (IOException exception) {
            throw new RuntimeException("Unable to authorize with google api", exception);
        }
    }
}
