package com.realarbird.spotimc.lastfm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.spotify.PlaybackState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles communication with the Last.fm REST API for Basic Mode.
 * All network calls operate with strict timeouts to prevent freezing the game client.
 */
public class LastFmAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/LastFm");
    private static final String API_BASE = "https://ws.audioscrobbler.com/2.0/";

    private final HttpClient httpClient;
    private final Map<String, String> artworkUrlCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService executorService;
    private volatile PlaybackState currentPlayback = PlaybackState.EMPTY;

    public LastFmAPI() {
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
            Thread t = new Thread(r, "LastFmAPI-Poller");
            t.setDaemon(true);
            return t;
        });

        executorService.scheduleAtFixedRate(this::pollRecentTracks, 0, 3, TimeUnit.SECONDS);
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

    private void pollRecentTracks() {
        SpotiMCConfig config = SpotiMCConfig.getInstance();
        String apiKey = config.lastFmApiKey != null ? config.lastFmApiKey.trim() : "";
        String username = config.lastFmUsername != null ? config.lastFmUsername.trim() : "";

        if (apiKey.isEmpty() || username.isEmpty()) {
            currentPlayback = PlaybackState.EMPTY;
            return;
        }

        try {
            String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String url = API_BASE + "?method=user.getrecenttracks&user=" + encodedUser + "&api_key=" + encodedKey + "&format=json&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "SpotiMC/1.0 (Minecraft Fabric Mod)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("recenttracks") && json.getAsJsonObject("recenttracks").has("track")) {
                    JsonArray tracks = json.getAsJsonObject("recenttracks").getAsJsonArray("track");
                    if (!tracks.isEmpty()) {
                        JsonObject trackObj = tracks.get(0).getAsJsonObject();

                        String trackName = trackObj.has("name") ? trackObj.get("name").getAsString() : "";
                        
                        String artistName = "";
                        if (trackObj.has("artist")) {
                            JsonElement artistElem = trackObj.get("artist");
                            if (artistElem.isJsonObject() && artistElem.getAsJsonObject().has("#text")) {
                                artistName = artistElem.getAsJsonObject().get("#text").getAsString();
                            } else if (artistElem.isJsonObject() && artistElem.getAsJsonObject().has("name")) {
                                artistName = artistElem.getAsJsonObject().get("name").getAsString();
                            } else if (artistElem.isJsonPrimitive()) {
                                artistName = artistElem.getAsString();
                            }
                        }

                        String albumName = "";
                        if (trackObj.has("album") && trackObj.get("album").isJsonObject()) {
                            JsonObject albumObj = trackObj.getAsJsonObject("album");
                            if (albumObj.has("#text")) {
                                albumName = albumObj.get("#text").getAsString();
                            }
                        }

                        String albumArtUrl = "";
                        if (trackObj.has("image") && trackObj.get("image").isJsonArray()) {
                            albumArtUrl = selectLargestImage(trackObj.getAsJsonArray("image"));
                        }

                        // user.getrecenttracks regularly returns empty image fields. Look
                        // up the current track once and cache its real album image instead
                        // of displaying the same SpotiMC fallback cover for every song.
                        if (!isUsableAlbumArtUrl(albumArtUrl)) {
                            albumArtUrl = resolveAlbumArtUrl(apiKey, artistName, albumName, trackName);
                        }

                        if (albumArtUrl.isEmpty()) {
                            System.out.println("[SpotiMC/LastFm] No album art URL resolved for Last.fm track '" + trackName + "' by '" + artistName + "'");
                        } else {
                            System.out.println("[SpotiMC/LastFm] Resolved Last.fm album art URL for track '" + trackName + "': " + albumArtUrl);
                        }

                        boolean isNowPlaying = false;
                        if (trackObj.has("@attr") && trackObj.get("@attr").isJsonObject()) {
                            JsonObject attr = trackObj.getAsJsonObject("@attr");
                            if (attr.has("nowplaying") && "true".equalsIgnoreCase(attr.get("nowplaying").getAsString())) {
                                isNowPlaying = true;
                            }
                        }

                        currentPlayback = new PlaybackState(
                                trackName,
                                artistName,
                                albumName,
                                albumArtUrl,
                                isNowPlaying,
                                0,
                                0
                        );
                        return;
                    }
                }
            }
            currentPlayback = PlaybackState.EMPTY;
        } catch (Exception e) {
            System.err.println("[SpotiMC/LastFm] Failed to poll Last.fm tracks: " + e);
            e.printStackTrace(System.err);
        }
    }

    private String resolveAlbumArtUrl(String apiKey, String artistName, String albumName, String trackName) {
        if (artistName == null || artistName.isBlank() || trackName == null || trackName.isBlank()) {
            return "";
        }

        String cacheKey = (artistName + "\u0000" + albumName + "\u0000" + trackName).toLowerCase(java.util.Locale.ROOT);
        String cached = artworkUrlCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String artworkUrl = "";
        try {
            String url = API_BASE + "?method=track.getInfo"
                    + "&artist=" + URLEncoder.encode(artistName, StandardCharsets.UTF_8)
                    + "&track=" + URLEncoder.encode(trackName, StandardCharsets.UTF_8)
                    + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&format=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "SpotiMC/1.0 (Minecraft Fabric Mod)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("track") && json.get("track").isJsonObject()) {
                    JsonObject track = json.getAsJsonObject("track");
                    if (track.has("album") && track.get("album").isJsonObject()) {
                        JsonObject album = track.getAsJsonObject("album");
                        if (album.has("image") && album.get("image").isJsonArray()) {
                            artworkUrl = selectLargestImage(album.getAsJsonArray("image"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not look up album art for {} - {}", artistName, trackName, e);
        }

        if (!isUsableAlbumArtUrl(artworkUrl)) {
            artworkUrl = "";
        }
        artworkUrlCache.put(cacheKey, artworkUrl);
        return artworkUrl;
    }

    private static String selectLargestImage(JsonArray images) {
        String fallback = "";
        String largest = "";
        for (JsonElement imgElem : images) {
            if (imgElem == null || !imgElem.isJsonObject()) continue;
            JsonObject image = imgElem.getAsJsonObject();
            String url = image.has("#text") && !image.get("#text").isJsonNull() ? image.get("#text").getAsString() : "";
            if (!isUsableAlbumArtUrl(url)) continue;

            String size = image.has("size") && !image.get("size").isJsonNull() ? image.get("size").getAsString() : "";
            if ("extralarge".equalsIgnoreCase(size) || "mega".equalsIgnoreCase(size)) {
                largest = url;
            } else if (fallback.isEmpty()) {
                fallback = url;
            }
        }
        return largest.isEmpty() ? fallback : largest;
    }

    private static boolean isUsableAlbumArtUrl(String url) {
        return url != null && !url.isBlank()
                // Last.fm's common "no image available" placeholder should be treated
                // as missing so the track-info lookup has a chance to find the cover.
                && !url.contains("2a96cbd8b46e442fc41c2b86b821562f");
    }
}
