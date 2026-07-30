package com.realarbird.spotimc;

import com.realarbird.spotimc.hud.SpotiMCHud;
import com.realarbird.spotimc.keybind.SpotiMCKeybinds;
import com.realarbird.spotimc.spotify.SpotifyAPI;
import com.realarbird.spotimc.spotify.SpotifyAuth;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpotiMC — Client-side Spotify integration for Minecraft.
 *
 * <p>This is the main client entrypoint that initializes all subsystems:
 * configuration, Spotify authentication, API polling, keybindings, and
 * the HUD overlay.</p>
 */
public class SpotiMCClient implements ClientModInitializer {

    public static final String MOD_ID = "spotimc";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");

    public static SpotiMCConfig CONFIG;
    public static SpotifyAuth SPOTIFY_AUTH;
    public static SpotifyAPI SPOTIFY_API;
    public static SpotiMCHud HUD;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing SpotiMC...");

        // 1. Load configuration
        CONFIG = SpotiMCConfig.getInstance();
        SpotiMCConfig.init(
                Minecraft.getInstance().gameDirectory.toPath().resolve("config")
        );
        CONFIG = SpotiMCConfig.getInstance(); // re-fetch after load

        // 2. Initialize Spotify authentication
        SPOTIFY_AUTH = new SpotifyAuth();
        SPOTIFY_AUTH.setOnAuthenticated(() -> {
            LOGGER.info("Spotify authentication successful!");
            // Save tokens to config
            CONFIG.accessToken = SPOTIFY_AUTH.getAccessToken();
            CONFIG.refreshToken = SPOTIFY_AUTH.getRefreshTokenValue();
            CONFIG.tokenExpiresAt = SPOTIFY_AUTH.getExpiresAt();
            SpotiMCConfig.save();
            // Start polling for playback data
            SPOTIFY_API.startPolling();
        });

        // Restore tokens from config if available
        if (CONFIG.hasStoredTokens()) {
            LOGGER.info("Restoring Spotify session from saved tokens...");
            SPOTIFY_AUTH.setTokens(CONFIG.accessToken, CONFIG.refreshToken, CONFIG.tokenExpiresAt);
        }

        // 3. Initialize Spotify API
        SPOTIFY_API = new SpotifyAPI(SPOTIFY_AUTH);

        // If we have valid tokens, start polling immediately
        if (SPOTIFY_AUTH.isAuthenticated()) {
            SPOTIFY_API.startPolling();
        }

        // 4. Initialize HUD and register with Fabric's HUD Element system
        HUD = new SpotiMCHud();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "spotify_hud"),
                HUD
        );

        // 5. Register keybindings
        SpotiMCKeybinds.register();

        LOGGER.info("SpotiMC initialized successfully!");
    }
}
