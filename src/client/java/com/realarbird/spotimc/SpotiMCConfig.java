package com.realarbird.spotimc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration manager for SpotiMC.
 * Stores HUD position/scale/visibility and Spotify OAuth tokens & credentials.
 * Persisted as JSON to .minecraft/config/spotimc.json.
 */
public class SpotiMCConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SpotiMCConfig instance;
    private static Path configPath;

    // --- HUD Settings ---
    public int hudX = 10;
    public int hudY = 10;
    public float hudScale = 1.0f;
    public boolean hudVisible = true;

    // --- Spotify Credentials & Tokens (persisted in spotimc.json) ---
    public String clientId = "";
    public String clientSecret = "";
    public String accessToken = "";
    public String refreshToken = "";
    public long tokenExpiresAt = 0;

    private SpotiMCConfig() {
    }

    /**
     * Returns the singleton config instance.
     */
    public static SpotiMCConfig getInstance() {
        if (instance == null) {
            instance = new SpotiMCConfig();
        }
        return instance;
    }

    /**
     * Initializes the config path and loads existing config from disk.
     *
     * @param gameConfigDir the .minecraft/config directory path
     */
    public static void init(Path gameConfigDir) {
        configPath = gameConfigDir.resolve("spotimc.json");
        load();
    }

    /**
     * Loads config from disk, creating defaults if the file doesn't exist.
     */
    public static void load() {
        if (configPath == null) {
            LOGGER.warn("Config path not initialized, using defaults");
            return;
        }

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                instance = GSON.fromJson(json, SpotiMCConfig.class);
                if (instance == null) {
                    instance = new SpotiMCConfig();
                }
                LOGGER.info("Loaded SpotiMC config from {}", configPath);
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                LOGGER.error("Failed to load SpotiMC config, using defaults", e);
                instance = new SpotiMCConfig();
            }
        } else {
            instance = new SpotiMCConfig();
            save();
            LOGGER.info("Created default SpotiMC config at {}", configPath);
        }
    }

    /**
     * Saves the current config to disk.
     */
    public static void save() {
        if (configPath == null || instance == null) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(instance));
        } catch (IOException e) {
            LOGGER.error("Failed to save SpotiMC config", e);
        }
    }

    /**
     * Checks whether we have stored Spotify tokens that might still be valid.
     */
    public boolean hasStoredTokens() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    /**
     * Clears all stored Spotify tokens.
     */
    public void clearTokens() {
        accessToken = "";
        refreshToken = "";
        tokenExpiresAt = 0;
        save();
    }

    /**
     * Resets HUD position to defaults.
     */
    public void resetHudPosition() {
        hudX = 10;
        hudY = 10;
        hudScale = 1.0f;
        save();
    }
}
