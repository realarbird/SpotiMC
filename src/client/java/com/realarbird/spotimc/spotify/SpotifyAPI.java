package com.realarbird.spotimc.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpotifyAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");
    private static final String API_BASE = "https://api.spotify.com/v1";

    private final SpotifyAuth auth;
    private final HttpClient httpClient;
    private ScheduledExecutorService executorService;

    private volatile PlaybackState currentPlayback = PlaybackState.EMPTY;

    public record TrackSearchResult(String id, String name, String artistName, String uri) {}
    public record PlaylistSearchResult(String id, String name, int trackCount, String uri) {}

    public SpotifyAPI(SpotifyAuth auth) {
        this.auth = auth;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public void startPolling() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SpotifyAPI-Poller");
            t.setDaemon(true);
            return t;
        });

        executorService.scheduleAtFixedRate(this::pollPlaybackState, 0, 2, TimeUnit.SECONDS);
    }

    public void stopPolling() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    public void shutdown() {
        stopPolling();
    }

    public PlaybackState getCurrentPlayback() {
        return currentPlayback;
    }

    private void pollPlaybackState() {
        String token = auth.getAccessToken();
        if (token == null) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/me/player"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                currentPlayback = PlaybackState.fromJson(json);
            } else if (response.statusCode() == 204) {
                currentPlayback = PlaybackState.EMPTY;
            } else if (response.statusCode() == 401) {
                LOGGER.warn("Spotify token unauthorized during polling, attempting refresh.");
                auth.refreshTokenAsync();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to poll Spotify playback state", e);
        }
    }

    public void nextTrack() {
        sendRequestAsync(API_BASE + "/me/player/next", "POST", null);
    }

    public void previousTrack() {
        sendRequestAsync(API_BASE + "/me/player/previous", "POST", null);
    }

    public void togglePlayPause() {
        boolean willPlay = !currentPlayback.isPlaying();
        String endpoint = willPlay ? "/me/player/play" : "/me/player/pause";
        sendRequestAsync(API_BASE + endpoint, "PUT", null);
    }

    public void setShuffle(boolean enable) {
        sendRequestAsync(API_BASE + "/me/player/shuffle?state=" + enable, "PUT", null);
    }

    public void setRepeat(String state) {
        // state can be "off", "context", "track"
        sendRequestAsync(API_BASE + "/me/player/repeat?state=" + state, "PUT", null);
    }

    public void playTrackUri(String trackUri) {
        String jsonBody = "{\"uris\":[\"" + trackUri + "\"]}";
        sendRequestAsync(API_BASE + "/me/player/play", "PUT", jsonBody);
    }

    public void playContextUri(String contextUri) {
        String jsonBody = "{\"context_uri\":\"" + contextUri + "\"}";
        sendRequestAsync(API_BASE + "/me/player/play", "PUT", jsonBody);
    }

    public void playTrackInContext(String contextUri, String trackUri) {
        String jsonBody = "{\"context_uri\":\"" + contextUri + "\",\"offset\":{\"uri\":\"" + trackUri + "\"}}";
        sendRequestAsync(API_BASE + "/me/player/play", "PUT", jsonBody);
    }

    public CompletableFuture<List<TrackSearchResult>> searchTracks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String token = auth.getAccessToken();
        if (token == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String url = API_BASE + "/search?q=" + encodedQuery + "&type=track&limit=20";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<TrackSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("tracks") && json.getAsJsonObject("tracks").has("items")) {
                                JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    JsonObject item = elem.getAsJsonObject();
                                    String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                                    String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "Unknown Track";
                                    String uri = item.has("uri") && !item.get("uri").isJsonNull() ? item.get("uri").getAsString() : "";

                                    String artist = "";
                                    if (item.has("artists") && item.get("artists").isJsonArray()) {
                                        JsonArray artists = item.getAsJsonArray("artists");
                                        if (!artists.isEmpty() && artists.get(0).isJsonObject()) {
                                            JsonObject artistObj = artists.get(0).getAsJsonObject();
                                            if (artistObj.has("name") && !artistObj.get("name").isJsonNull()) {
                                                artist = artistObj.get("name").getAsString();
                                            }
                                        }
                                    }

                                    results.add(new TrackSearchResult(id, name, artist, uri));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse track search response", e);
                        }
                    }
                    return results;
                });
    }

    public CompletableFuture<List<PlaylistSearchResult>> getUserPlaylists() {
        String token = auth.getAccessToken();
        if (token == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        String url = API_BASE + "/me/playlists?limit=30";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<PlaylistSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("items")) {
                                JsonArray items = json.getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    JsonObject item = elem.getAsJsonObject();
                                    String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                                    String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "Untitled Playlist";
                                    String uri = item.has("uri") && !item.get("uri").isJsonNull() ? item.get("uri").getAsString() : "";

                                    int trackCount = 0;
                                    if (item.has("tracks") && item.get("tracks").isJsonObject()) {
                                        JsonObject tracksObj = item.getAsJsonObject("tracks");
                                        if (tracksObj.has("total") && !tracksObj.get("total").isJsonNull()) {
                                            trackCount = tracksObj.get("total").getAsInt();
                                        } else if (tracksObj.has("items") && tracksObj.get("items").isJsonArray()) {
                                            trackCount = tracksObj.getAsJsonArray("items").size();
                                        }
                                    }

                                    results.add(new PlaylistSearchResult(id, name, trackCount, uri));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse user playlists response", e);
                        }
                    }
                    return results;
                });
    }

    public CompletableFuture<List<TrackSearchResult>> getPlaylistTracks(String playlistId) {
        String token = auth.getAccessToken();
        if (token == null || playlistId == null || playlistId.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String url = API_BASE + "/playlists/" + playlistId + "/tracks?limit=50";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<TrackSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("items") && json.get("items").isJsonArray()) {
                                JsonArray items = json.getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    JsonObject wrapper = elem.getAsJsonObject();
                                    if (wrapper.has("track") && wrapper.get("track").isJsonObject()) {
                                        JsonObject trackObj = wrapper.getAsJsonObject("track");
                                        String id = trackObj.has("id") && !trackObj.get("id").isJsonNull() ? trackObj.get("id").getAsString() : "";
                                        String name = trackObj.has("name") && !trackObj.get("name").isJsonNull() ? trackObj.get("name").getAsString() : "Unknown Track";
                                        String uri = trackObj.has("uri") && !trackObj.get("uri").isJsonNull() ? trackObj.get("uri").getAsString() : "";

                                        String artist = "";
                                        if (trackObj.has("artists") && trackObj.get("artists").isJsonArray()) {
                                            JsonArray artists = trackObj.getAsJsonArray("artists");
                                            if (!artists.isEmpty() && artists.get(0).isJsonObject()) {
                                                JsonObject artistObj = artists.get(0).getAsJsonObject();
                                                if (artistObj.has("name") && !artistObj.get("name").isJsonNull()) {
                                                    artist = artistObj.get("name").getAsString();
                                                }
                                            }
                                        }

                                        results.add(new TrackSearchResult(id, name, artist, uri));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse playlist tracks response", e);
                        }
                    }
                    return results;
                });
    }

    private void sendRequestAsync(String url, String method, String jsonBody) {
        String token = auth.getAccessToken();
        if (token == null) {
            LOGGER.warn("Cannot send request to {}: Not authenticated", url);
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token);

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.POST(jsonBody != null ? HttpRequest.BodyPublishers.ofString(jsonBody) : HttpRequest.BodyPublishers.noBody());
        } else if ("PUT".equalsIgnoreCase(method)) {
            if (jsonBody != null) {
                requestBuilder.header("Content-Type", "application/json");
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
            }
        }

        HttpRequest request = requestBuilder.build();

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 401) {
                    LOGGER.warn("Spotify token unauthorized for request {}, attempting refresh.", url);
                    auth.refreshTokenAsync();
                } else if (response.statusCode() >= 400) {
                    LOGGER.warn("Spotify API error for {}: {} {}", url, response.statusCode(), response.body());
                } else {
                    pollPlaybackState();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to execute Spotify API request to {}", url, e);
            }
        });
    }
}
