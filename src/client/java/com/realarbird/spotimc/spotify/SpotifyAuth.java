package com.realarbird.spotimc.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.realarbird.spotimc.SpotiMCConfig;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Spotify OAuth2 Authentication using PKCE (Authorization Code Flow with Proof Key for Code Exchange).
 *
 * <p>PKCE allows secure authentication without exposing client secrets in source code or client apps.</p>
 */
public class SpotifyAuth {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");

    // Default Client ID for SpotiMC (can be overridden via spotimc.json)
    private static final String DEFAULT_CLIENT_ID = "93b30bcdb38748d8892107bd8a124aee";
    private static final String REDIRECT_URI = "http://127.0.0.1:4381/callback";
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private playlist-read-collaborative";

    private final HttpClient httpClient;

    private String accessToken;
    private String refreshToken;
    private long expiresAt;

    private Runnable onAuthenticated;
    private HttpServer authServer;
    private String expectedState;
    private String codeVerifier;

    public SpotifyAuth() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void setOnAuthenticated(Runnable onAuthenticated) {
        this.onAuthenticated = onAuthenticated;
    }

    public synchronized void setTokens(String accessToken, String refreshToken, long expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public synchronized String getAccessTokenValue() {
        return accessToken;
    }

    public synchronized String getRefreshTokenValue() {
        return refreshToken;
    }

    public synchronized long getExpiresAt() {
        return expiresAt;
    }

    public synchronized boolean isAuthenticated() {
        return accessToken != null && !accessToken.isEmpty() && refreshToken != null && !refreshToken.isEmpty();
    }

    public String getClientId() {
        SpotiMCConfig config = SpotiMCConfig.getInstance();
        if (config.clientId != null && !config.clientId.trim().isEmpty()) {
            return config.clientId.trim();
        }
        return DEFAULT_CLIENT_ID;
    }

    public String getClientSecret() {
        SpotiMCConfig config = SpotiMCConfig.getInstance();
        return config.clientSecret != null ? config.clientSecret.trim() : "";
    }

    /**
     * Generates a random PKCE code verifier (RFC 7636).
     */
    private static String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] code = new byte[32];
        random.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    /**
     * Generates the SHA-256 PKCE code challenge (RFC 7636).
     */
    private static String generateCodeChallenge(String verifier) {
        try {
            byte[] bytes = verifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 algorithm missing for PKCE challenge", e);
            return verifier;
        }
    }

    /**
     * Starts the Spotify PKCE OAuth2 browser authorization flow.
     */
    public void startAuth() {
        try {
            expectedState = UUID.randomUUID().toString();
            codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);

            String clientId = getClientId();

            String url = "https://accounts.spotify.com/authorize" +
                    "?response_type=code" +
                    "&client_id=" + clientId +
                    "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8) +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&state=" + expectedState +
                    "&code_challenge_method=S256" +
                    "&code_challenge=" + codeChallenge;

            startCallbackServer();

            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", url});
                    } else if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                    } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to open browser for Spotify authentication.", e);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to start Spotify authentication.", e);
        }
    }

    private void startCallbackServer() throws IOException {
        if (authServer != null) {
            authServer.stop(0);
        }

        authServer = HttpServer.create(new InetSocketAddress(4381), 0);
        authServer.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String response = "Authentication failed. Please try again.";
            int statusCode = 400;

            if (query != null) {
                String code = null;
                String state = null;
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length > 1) {
                        if ("code".equals(kv[0])) code = kv[1];
                        if ("state".equals(kv[0])) state = kv[1];
                    }
                }

                if (expectedState != null && expectedState.equals(state) && code != null) {
                    exchangeToken(code);
                    response = "Authentication successful! You can close this tab and return to Minecraft.";
                    statusCode = 200;
                }
            }

            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }

            CompletableFuture.runAsync(() -> authServer.stop(1));
        });

        authServer.start();
    }

    private void exchangeToken(String code) {
        String clientId = getClientId();
        String body = "grant_type=authorization_code" +
                "&client_id=" + clientId +
                "&code=" + code +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                "&code_verifier=" + codeVerifier;

        String clientSecret = getClientSecret();
        if (!clientSecret.isEmpty()) {
            body += "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        }

        sendTokenRequest(body);
    }

    public synchronized CompletableFuture<Void> refreshToken() {
        if (refreshToken == null) {
            return CompletableFuture.completedFuture(null);
        }

        String clientId = getClientId();
        StringBuilder sb = new StringBuilder();
        sb.append("grant_type=refresh_token")
          .append("&client_id=").append(clientId)
          .append("&refresh_token=").append(refreshToken);

        String clientSecret = getClientSecret();
        if (!clientSecret.isEmpty()) {
            sb.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        }

        final String tokenBody = sb.toString();
        return CompletableFuture.runAsync(() -> sendTokenRequest(tokenBody));
    }

    private void sendTokenRequest(String body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        String clientSecret = getClientSecret();
        if (!clientSecret.isEmpty()) {
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((getClientId() + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", authHeader);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String newAccessToken = json.get("access_token").getAsString();
                long expiresIn = json.get("expires_in").getAsLong();

                String newRefreshToken = refreshToken;
                if (json.has("refresh_token")) {
                    newRefreshToken = json.get("refresh_token").getAsString();
                }

                setTokens(newAccessToken, newRefreshToken, System.currentTimeMillis() + (expiresIn * 1000L));
                LOGGER.info("Successfully updated Spotify tokens.");

                if (onAuthenticated != null) {
                    onAuthenticated.run();
                }
            } else {
                LOGGER.error("Failed to get tokens: {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Exception during token request", e);
        }
    }

    public synchronized String getAccessToken() {
        if (!isAuthenticated()) {
            return null;
        }

        if (System.currentTimeMillis() > expiresAt - 30000) {
            refreshToken().join();
        }

        return accessToken;
    }
}
