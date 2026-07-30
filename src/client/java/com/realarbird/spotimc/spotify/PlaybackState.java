package com.realarbird.spotimc.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record PlaybackState(
        String trackName,
        String artistName,
        String albumName,
        String albumArtUrl,
        boolean isPlaying,
        long progressMs,
        long durationMs,
        boolean shuffleState,
        String repeatState // "off", "track", "context"
) {
    public static final PlaybackState EMPTY = new PlaybackState(
            "", "", "", "", false, 0, 0, false, "off"
    );

    public PlaybackState(String trackName, String artistName, String albumName, String albumArtUrl, boolean isPlaying, long progressMs, long durationMs) {
        this(trackName, artistName, albumName, albumArtUrl, isPlaying, progressMs, durationMs, false, "off");
    }

    public static PlaybackState fromJson(JsonObject json) {
        if (json == null || !json.has("item") || json.get("item").isJsonNull()) {
            return EMPTY;
        }

        try {
            boolean isPlaying = json.has("is_playing") && !json.get("is_playing").isJsonNull() && json.get("is_playing").getAsBoolean();
            long progressMs = json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsLong() : 0;

            boolean shuffleState = json.has("shuffle_state") && !json.get("shuffle_state").isJsonNull() && json.get("shuffle_state").getAsBoolean();
            String repeatState = json.has("repeat_state") && !json.get("repeat_state").isJsonNull() ? json.get("repeat_state").getAsString() : "off";

            JsonObject item = json.getAsJsonObject("item");
            String trackName = item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "";
            long durationMs = item.has("duration_ms") && !item.get("duration_ms").isJsonNull() ? item.get("duration_ms").getAsLong() : 0;

            String artistName = "";
            if (item.has("artists") && item.get("artists").isJsonArray()) {
                JsonArray artists = item.getAsJsonArray("artists");
                if (!artists.isEmpty() && artists.get(0).isJsonObject()) {
                    JsonObject firstArtist = artists.get(0).getAsJsonObject();
                    if (firstArtist.has("name") && !firstArtist.get("name").isJsonNull()) {
                        artistName = firstArtist.get("name").getAsString();
                    }
                }
            }

            String albumName = "";
            String albumArtUrl = "";
            if (item.has("album") && item.get("album").isJsonObject()) {
                JsonObject album = item.getAsJsonObject("album");
                if (album.has("name") && !album.get("name").isJsonNull()) {
                    albumName = album.get("name").getAsString();
                }
                
                if (album.has("images") && album.get("images").isJsonArray()) {
                    albumArtUrl = extractBestImageUrl(album.getAsJsonArray("images"));
                }
            }

            if (albumArtUrl.isEmpty() && item.has("show") && item.get("show").isJsonObject()) {
                JsonObject show = item.getAsJsonObject("show");
                if (albumName.isEmpty() && show.has("name") && !show.get("name").isJsonNull()) {
                    albumName = show.get("name").getAsString();
                }
                if (show.has("images") && show.get("images").isJsonArray()) {
                    albumArtUrl = extractBestImageUrl(show.getAsJsonArray("images"));
                }
            }

            if (albumArtUrl.isEmpty() && item.has("images") && item.get("images").isJsonArray()) {
                albumArtUrl = extractBestImageUrl(item.getAsJsonArray("images"));
            }

            return new PlaybackState(trackName, artistName, albumName, albumArtUrl, isPlaying, progressMs, durationMs, shuffleState, repeatState);
        } catch (Exception e) {
            return EMPTY;
        }
    }

    private static String extractBestImageUrl(JsonArray images) {
        if (images == null || images.isEmpty()) return "";
        String fallback = "";
        for (JsonElement elem : images) {
            if (elem == null || !elem.isJsonObject()) continue;
            JsonObject imgObj = elem.getAsJsonObject();
            if (!imgObj.has("url") || imgObj.get("url").isJsonNull()) continue;
            String url = imgObj.get("url").getAsString();
            if (url == null || url.isBlank()) continue;

            int width = imgObj.has("width") && !imgObj.get("width").isJsonNull() ? imgObj.get("width").getAsInt() : 0;
            if (width == 300 || width == 64) {
                return url;
            }
            if (fallback.isEmpty()) {
                fallback = url;
            }
        }
        return fallback;
    }
}
