package com.realarbird.spotimc;

import com.realarbird.spotimc.hud.SpotiMCHud;
import com.realarbird.spotimc.keybind.SpotiMCKeybinds;
import com.realarbird.spotimc.lastfm.LastFmAPI;
import com.realarbird.spotimc.network.SpotiMCSongPayload;
import com.realarbird.spotimc.social.ClientSongTracker;
import com.realarbird.spotimc.spotify.PlaybackState;
import com.realarbird.spotimc.spotify.SpotifyAPI;
import com.realarbird.spotimc.spotify.SpotifyAuth;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpotiMC — Client entrypoint initializing configuration, authentication, API pollers,
 * social song networking, HUD overlay, and keybindings.
 */
public class SpotiMCClient implements ClientModInitializer {

    public static final String MOD_ID = "spotimc";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");

    public static SpotiMCConfig CONFIG;
    public static SpotifyAuth SPOTIFY_AUTH;
    public static SpotifyAPI SPOTIFY_API;
    public static LastFmAPI LASTFM_API;
    public static SpotiMCHud HUD;

    @Override
    public void onInitializeClient() {
        System.out.println("[SpotiMC] SpotiMCClient.onInitializeClient() ENTRY");
        try {
            LOGGER.info("Initializing SpotiMC client...");

            // 1. Load configuration
            CONFIG = SpotiMCConfig.getInstance();
            SpotiMCConfig.init(
                    Minecraft.getInstance().gameDirectory.toPath().resolve("config")
            );
            CONFIG = SpotiMCConfig.getInstance();

            LOGGER.info("SpotiMC config loaded. Mode: {}, HUD visible: {}", CONFIG.mode, CONFIG.hudVisible);

            // 2. Initialize Spotify authentication & API
            SPOTIFY_AUTH = new SpotifyAuth();
            SPOTIFY_AUTH.setOnAuthenticated(() -> {
                LOGGER.info("Spotify authentication successful!");
                CONFIG.accessToken = SPOTIFY_AUTH.getAccessToken();
                CONFIG.refreshToken = SPOTIFY_AUTH.getRefreshTokenValue();
                CONFIG.tokenExpiresAt = SPOTIFY_AUTH.getExpiresAt();
                SpotiMCConfig.save();
                if (CONFIG.isAdvancedMode()) {
                    SPOTIFY_API.startPolling();
                }
            });

            if (CONFIG.hasStoredTokens()) {
                LOGGER.info("Restoring Spotify session from saved tokens...");
                SPOTIFY_AUTH.setTokens(CONFIG.accessToken, CONFIG.refreshToken, CONFIG.tokenExpiresAt);
            }

            SPOTIFY_API = new SpotifyAPI(SPOTIFY_AUTH);

            // 3. Initialize Last.fm API
            LASTFM_API = new LastFmAPI();

            // Start active poller based on mode
            LOGGER.info("Starting active poller for mode: {}", CONFIG.mode);
            updateActiveMode();

            // 4. Register client-side networking receiver for player overhead song display
            ClientPlayNetworking.registerGlobalReceiver(SpotiMCSongPayload.TYPE, (payload, context) -> {
                context.client().execute(() -> ClientSongTracker.updateSong(payload));
            });

            // 5. Register connection lifecycle events
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                client.execute(() -> {
                    PlaybackState state = getActivePlayback();
                    if (state != null) {
                        ClientSongTracker.forceBroadcast(state.trackName(), state.artistName(), state.isPlaying());
                    }
                });
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                client.execute(ClientSongTracker::clearAll);
            });

            // 6. Register tick handler to broadcast local song info and process social tracking
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                PlaybackState state = getActivePlayback();
                if (state != null) {
                    ClientSongTracker.tickBroadcast(state.trackName(), state.artistName(), state.isPlaying());
                } else {
                    ClientSongTracker.tickBroadcast("", "", false);
                }
            });

            // 7. Initialize HUD overlay
            HUD = new SpotiMCHud();
            HudElementRegistry.addLast(
                    Identifier.fromNamespaceAndPath(MOD_ID, "spotify_hud"),
                    HUD
            );

            // 8. Register keybindings
            SpotiMCKeybinds.register();

            LOGGER.info("SpotiMC client initialized successfully!");
            System.out.println("[SpotiMC] SpotiMCClient.onInitializeClient() completed successfully");
        } catch (Exception e) {
            LOGGER.error("[SpotiMC] FATAL: SpotiMCClient.onInitializeClient() failed!", e);
            System.err.println("[SpotiMC] FATAL: SpotiMCClient.onInitializeClient() failed: " + e);
            e.printStackTrace(System.err);
        }
    }

    /**
     * Updates active API poller based on the selected mode in CONFIG.
     */
    public static void updateActiveMode() {
        if (CONFIG == null) return;

        if (CONFIG.isAdvancedMode()) {
            if (LASTFM_API != null) LASTFM_API.stopPolling();
            if (SPOTIFY_API != null && SPOTIFY_AUTH != null && SPOTIFY_AUTH.isAuthenticated()) {
                SPOTIFY_API.startPolling();
            }
        } else {
            if (SPOTIFY_API != null) SPOTIFY_API.stopPolling();
            if (LASTFM_API != null) LASTFM_API.startPolling();
        }
    }

    /**
     * Returns the currently active playback state from either Spotify (Advanced Mode) or Last.fm (Basic Mode).
     */
    public static PlaybackState getActivePlayback() {
        if (CONFIG != null && CONFIG.isAdvancedMode()) {
            return SPOTIFY_API != null ? SPOTIFY_API.getCurrentPlayback() : PlaybackState.EMPTY;
        } else {
            return LASTFM_API != null ? LASTFM_API.getCurrentPlayback() : PlaybackState.EMPTY;
        }
    }
}

