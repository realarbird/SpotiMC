package com.realarbird.spotimc.hud;

import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.spotify.PlaybackState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Renders the SpotiMC playback HUD overlay as a Fabric HudElement.
 * Supports both Basic Mode (Last.fm) and Advanced Mode (Spotify).
 */
public class SpotiMCHud implements HudElement {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SpotiMC/HUD");
    public static final Identifier DEFAULT_COVER = Identifier.fromNamespaceAndPath("spotimc", "textures/gui/default_cover.png");
    /** The default cover PNG is 64x64 pixels. */
    private static final int DEFAULT_COVER_SIZE = 64;

    private static SpotiMCHud instance;
    private final AlbumArtTexture albumArtTexture;
    private String lastLoggedArtState = "";

    public SpotiMCHud() {
        this.albumArtTexture = new AlbumArtTexture();
        instance = this;
    }

    public static SpotiMCHud getInstance() {
        return instance;
    }

    /**
     * Truncates a string to fit within maxPixelWidth, appending "..." if it overflows.
     */
    public static String trimToWidth(Font font, String text, int maxPixelWidth) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(text) <= maxPixelWidth) return text;
        int dotsWidth = font.width("...");
        String trimmed = font.plainSubstrByWidth(text, maxPixelWidth - dotsWidth);
        return trimmed + "...";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
        SpotiMCConfig config = SpotiMCConfig.getInstance();
        if (!config.hudVisible) {
            return;
        }

        PlaybackState playback = SpotiMCClient.getActivePlayback();
        if (playback == null || playback.equals(PlaybackState.EMPTY)) {
            return;
        }

        int hudX = config.hudX;
        int hudY = config.hudY;
        float scale = config.hudScale;

        int width = 180;
        int height = 50;

        // Apply scaling and translation via the pose matrix
        gfx.pose().pushMatrix();
        gfx.pose().translate(hudX, hudY);
        gfx.pose().scale(scale, scale);

        // Background — semi-transparent dark panel
        gfx.fill(0, 0, width, height, 0xCC1A1A2E);

        // Album Art (32x32 at position 9,9)
        // Use the 12-parameter blit overload so we can specify separate draw
        // size (32x32) and source region size (full texture) to sample the
        // entire texture rather than just the top-left quarter.
        String artUrl = playback.albumArtUrl();
        AlbumArtTexture.CachedTexture cachedArt = null;
        if (artUrl != null && !artUrl.isEmpty()) {
            cachedArt = albumArtTexture.getCachedTexture(artUrl);
        }

        if (cachedArt != null) {
            String currentState = "LOADED:" + artUrl;
            if (!currentState.equals(lastLoggedArtState)) {
                LOGGER.info("[SpotiMCHud] Displaying custom album art texture ({}) for track '{}' (URL: {})",
                        cachedArt.id(), playback.trackName(), artUrl);
                lastLoggedArtState = currentState;
            }

            // Draw the downloaded album art. Source region = full texture.
            gfx.blit(RenderPipelines.GUI_TEXTURED, cachedArt.id(),
                    9, 9,                               // draw position (x, y)
                    0.0F, 0.0F,                         // UV offset
                    32, 32,                              // draw size (width, height)
                    cachedArt.width(), cachedArt.height(), // source region size
                    cachedArt.width(), cachedArt.height()  // texture dimensions
            );
        } else {
            // Reason logging for fallback cover
            String reason;
            if (artUrl == null || artUrl.isEmpty()) {
                reason = "No album art URL provided by API for track '" + playback.trackName() + "' by '" + playback.artistName() + "'";
            } else if (albumArtTexture.isFailed(artUrl)) {
                reason = "Album art download or decoding FAILED previously for URL: " + artUrl;
            } else if (albumArtTexture.isDownloading(artUrl)) {
                reason = "Album art is currently DOWNLOADING in background for URL: " + artUrl;
            } else {
                reason = "Album art download PENDING for URL: " + artUrl;
            }

            String currentState = "FALLBACK:" + (artUrl != null ? artUrl : "") + ":" + reason;
            if (!currentState.equals(lastLoggedArtState)) {
                LOGGER.info("[SpotiMCHud] Displaying FALLBACK cover picture (default_cover.png). Reason: {}", reason);
                lastLoggedArtState = currentState;
            }

            // Draw default cover picture — 64x64 PNG, sample the full texture
            gfx.blit(RenderPipelines.GUI_TEXTURED, DEFAULT_COVER,
                    9, 9,
                    0.0F, 0.0F,
                    32, 32,
                    DEFAULT_COVER_SIZE, DEFAULT_COVER_SIZE,
                    DEFAULT_COVER_SIZE, DEFAULT_COVER_SIZE
            );
        }

        // Track name and artist — truncated with ... if longer than 120px
        Font font = Minecraft.getInstance().font;
        String rawTrack = playback.trackName() != null && !playback.trackName().isEmpty() ? playback.trackName() : "Unknown Track";
        String rawArtist = playback.artistName() != null && !playback.artistName().isEmpty() ? playback.artistName() : "Unknown Artist";

        String trackName = trimToWidth(font, rawTrack, 120);
        String artistName = trimToWidth(font, rawArtist, 120);

        gfx.textRenderer().accept(50, 12, Component.literal(trackName));
        gfx.textRenderer().accept(50, 24, Component.literal(artistName));

        // Progress Bar / Mode Bar
        int progressWidth = 120;
        int progressHeight = 4;
        int progressX = 50;
        int progressY = 38;

        // Background bar (gray)
        gfx.fill(progressX, progressY, progressX + progressWidth, progressY + progressHeight, 0xFF555555);

        // Filled bar (Spotify green)
        if (playback.durationMs() > 0) {
            float ratio = (float) playback.progressMs() / playback.durationMs();
            ratio = Math.min(1.0f, Math.max(0.0f, ratio));
            int filledWidth = (int) (progressWidth * ratio);
            gfx.fill(progressX, progressY, progressX + filledWidth, progressY + progressHeight, 0xFF1DB954);
        } else if (playback.isPlaying()) {
            // Basic Mode active song indicator
            gfx.fill(progressX, progressY, progressX + progressWidth, progressY + progressHeight, 0xFF1DB954);
        }

        gfx.pose().popMatrix();
    }

    /**
     * Cleans up album art textures.
     */
    public void close() {
        albumArtTexture.close();
    }
}
