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
 * Stores HUD position/scale/visibility, operational mode (Basic vs Advanced),
 * Spotify OAuth credentials & tokens, Last.fm API settings, and Social Features.
 * Persisted as JSON to .minecraft/config/spotimc.json.
 */
public class SpotiMCConfig {

    public enum Mode {
        BASIC,
        ADVANCED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SpotiMCConfig instance;
    private static Path configPath;

    // --- Operational Mode ---
    public Mode mode = Mode.ADVANCED;

    // --- HUD Settings ---
    public int hudX = 10;
    public int hudY = 10;
    public float hudScale = 1.0f;
    public boolean hudVisible = true;

    // --- Social Features ---
    public boolean showOthersListeningStats = true;
    public boolean shareMyListeningStats = true;

    // --- Spotify Credentials & Tokens (Advanced Mode) ---
    public String clientId = "";
    public String clientSecret = "";
    public String accessToken = "";
    public String refreshToken = "";
    public long tokenExpiresAt = 0;

    // --- Last.fm Settings (Basic Mode) ---
    public String lastFmApiKey = "";
    public String lastFmUsername = "";

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
                if (instance.mode == null) {
                    instance.mode = Mode.ADVANCED;
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

    public boolean isAdvancedMode() {
        return mode == Mode.ADVANCED;
    }

    public boolean isBasicMode() {
        return mode == Mode.BASIC;
    }

    /**
     * Checks whether stored Spotify tokens exist.
     */
    public boolean hasStoredTokens() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    /**
     * Clears stored Spotify tokens.
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
