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
    public record LibraryResult<T>(List<T> items, String errorMessage) {
        public boolean succeeded() {
            return errorMessage == null || errorMessage.isEmpty();
        }
    }

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

    public CompletableFuture<LibraryResult<TrackSearchResult>> searchTracks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new LibraryResult<>(List.of(), "Enter a song title or artist."));
        }

        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        // Spotify's current Search API accepts at most 10 results per type. Requests
        // for 20 receive HTTP 400, which was previously shown as "No songs found".
        String url = API_BASE + "/search?q=" + encodedQuery + "&type=track&limit=10";

        return getJsonWithRefresh(url)
                .thenApply(response -> {
                    List<TrackSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("tracks") && json.getAsJsonObject("tracks").has("items")) {
                                JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    if (elem == null || !elem.isJsonObject()) continue;
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
                    } else {
                        LOGGER.warn("Spotify track search failed: HTTP {} {}", response.statusCode(), response.body());
                    }
                    return new LibraryResult<>(results, response.statusCode() == 200 ? "" : libraryError("Search", response.statusCode()));
                })
                .exceptionally(error -> libraryRequestFailed("search", error));
    }

    public CompletableFuture<LibraryResult<PlaylistSearchResult>> getUserPlaylists() {
        String url = API_BASE + "/me/playlists?limit=30";
        return getJsonWithRefresh(url)
                .thenApply(response -> {
                    List<PlaylistSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("items")) {
                                JsonArray items = json.getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    if (elem == null || !elem.isJsonObject()) continue;
                                    JsonObject item = elem.getAsJsonObject();
                                    String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                                    String name = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "Untitled Playlist";
                                    String uri = item.has("uri") && !item.get("uri").isJsonNull() ? item.get("uri").getAsString() : "";

                                    if (id.isEmpty() && uri.startsWith("spotify:playlist:")) {
                                        id = uri.substring("spotify:playlist:".length());
                                    }

                                    int trackCount = getPlaylistItemCount(item);

                                    if (uri.isEmpty() && !id.isEmpty()) {
                                        uri = "spotify:playlist:" + id;
                                    }

                                    results.add(new PlaylistSearchResult(id, name, trackCount, uri));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse user playlists response", e);
                        }
                    } else {
                        LOGGER.warn("Spotify playlists fetch failed: HTTP {} {}", response.statusCode(), response.body());
                    }
                    return new LibraryResult<>(results, response.statusCode() == 200 ? "" : libraryError("Playlist load", response.statusCode()));
                })
                .exceptionally(error -> libraryRequestFailed("playlist load", error));
    }

    public CompletableFuture<LibraryResult<TrackSearchResult>> getPlaylistTracks(String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) {
            return CompletableFuture.completedFuture(new LibraryResult<>(List.of(), "This playlist does not have a Spotify ID."));
        }

        // /tracks is deprecated. The current endpoint returns wrappers with an `item`
        // object, which the parser below handles alongside legacy `track` wrappers.
        String url = API_BASE + "/playlists/" + URLEncoder.encode(playlistId, StandardCharsets.UTF_8) + "/items?limit=50";
        return getJsonWithRefresh(url)
                .thenApply(response -> {
                    List<TrackSearchResult> results = new ArrayList<>();
                    if (response.statusCode() == 200 && response.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("items") && json.get("items").isJsonArray()) {
                                JsonArray items = json.getAsJsonArray("items");
                                for (JsonElement elem : items) {
                                    if (elem == null || !elem.isJsonObject()) continue;
                                    JsonObject wrapper = elem.getAsJsonObject();

                                    JsonObject trackObj = null;
                                    if (wrapper.has("track") && !wrapper.get("track").isJsonNull() && wrapper.get("track").isJsonObject()) {
                                        trackObj = wrapper.getAsJsonObject("track");
                                    } else if (wrapper.has("item") && !wrapper.get("item").isJsonNull() && wrapper.get("item").isJsonObject()) {
                                        trackObj = wrapper.getAsJsonObject("item");
                                    } else if (wrapper.has("name") && wrapper.has("uri")) {
                                        trackObj = wrapper;
                                    }

                                    if (trackObj == null) continue;

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

                                    if (!name.isEmpty() && !uri.isEmpty()) {
                                        results.add(new TrackSearchResult(id, name, artist, uri));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse playlist tracks response", e);
                        }
                    } else {
                        LOGGER.warn("Spotify playlist tracks fetch failed for playlist {}: HTTP {} {}", playlistId, response.statusCode(), response.body());
                    }
                    return new LibraryResult<>(results, response.statusCode() == 200 ? "" : libraryError("Playlist track load", response.statusCode()));
                })
                .exceptionally(error -> libraryRequestFailed("playlist track load", error));
    }

    private int getPlaylistItemCount(JsonObject playlist) {
        // Spotify migrated playlist summaries from `tracks` to `items`. Prefer the
        // current field and retain the legacy fallback for older responses.
        for (String key : List.of("items", "tracks")) {
            if (!playlist.has(key) || !playlist.get(key).isJsonObject()) continue;
            JsonObject items = playlist.getAsJsonObject(key);
            if (items.has("total") && !items.get("total").isJsonNull()) {
                return items.get("total").getAsInt();
            }
            if (items.has("items") && items.get("items").isJsonArray()) {
                return items.getAsJsonArray("items").size();
            }
        }
        return 0;
    }

    private CompletableFuture<HttpResponse<String>> getJsonWithRefresh(String url) {
        return getJsonWithRefresh(url, false);
    }

    /**
     * Performs a read request and retries it exactly once after a successful token
     * refresh. This keeps a transient expired access token from becoming an empty UI.
     */
    private CompletableFuture<HttpResponse<String>> getJsonWithRefresh(String url, boolean retriedAfterRefresh) {
        String token = auth.getAccessToken();
        if (token == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Spotify is not connected"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 401 || retriedAfterRefresh) {
                        return CompletableFuture.completedFuture(response);
                    }
                    LOGGER.warn("Spotify token unauthorized for {}, refreshing and retrying once.", url);
                    return auth.refreshTokenAsync().thenCompose(refreshed -> {
                        if (!refreshed) {
                            return CompletableFuture.completedFuture(response);
                        }
                        return getJsonWithRefresh(url, true);
                    });
                });
    }

    private static String libraryError(String operation, int statusCode) {
        return switch (statusCode) {
            case 401 -> "Spotify session expired. Reconnect Spotify and try again.";
            case 403 -> "Spotify denied access to this library item.";
            case 429 -> "Spotify is rate-limiting requests. Please wait a moment.";
            default -> operation + " failed (Spotify HTTP " + statusCode + ").";
        };
    }

    private static <T> LibraryResult<T> libraryRequestFailed(String operation, Throwable error) {
        LOGGER.warn("Spotify {} request failed", operation, error);
        return new LibraryResult<>(List.of(), "Could not " + operation + ". Check your Spotify connection and try again.");
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
