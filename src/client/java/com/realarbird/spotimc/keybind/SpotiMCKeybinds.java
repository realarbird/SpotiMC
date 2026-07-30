package com.realarbird.spotimc.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.screen.SpotiMCConfigScreen;
import com.realarbird.spotimc.screen.SpotiMCSearchScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and handles all SpotiMC keybindings.
 *
 * <p>Keybinds appear under the "SpotiMC" category in Minecraft's Controls menu.</p>
 */
public class SpotiMCKeybinds {

    /** Custom keybind category registered with Fabric */
    public static final KeyMapping.Category SPOTIMC_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("spotimc", "general")
    );

    public static KeyMapping nextTrackKey;
    public static KeyMapping previousTrackKey;
    public static KeyMapping playPauseKey;
    public static KeyMapping toggleHudKey;
    public static KeyMapping configKey;
    public static KeyMapping searchMenuKey;

    /**
     * Registers all keybindings and the tick handler that processes them.
     */
    public static void register() {
        nextTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT,
                SPOTIMC_CATEGORY
        ));

        previousTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.previous",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT,
                SPOTIMC_CATEGORY
        ));

        playPauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.playpause",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                SPOTIMC_CATEGORY
        ));

        toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                SPOTIMC_CATEGORY
        ));

        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                SPOTIMC_CATEGORY
        ));

        searchMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotimc.search_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                SPOTIMC_CATEGORY
        ));

        // Process keybinds each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Minecraft mc = Minecraft.getInstance();

            // Config key (works in all modes)
            while (configKey.consumeClick()) {
                mc.setScreenAndShow(
                        new SpotiMCConfigScreen(mc.gui != null ? mc.gui.screen() : null)
                );
            }

            // Toggle HUD (works in all modes)
            while (toggleHudKey.consumeClick()) {
                SpotiMCConfig config = SpotiMCConfig.getInstance();
                config.hudVisible = !config.hudVisible;
                SpotiMCConfig.save();
            }

            SpotiMCConfig config = SpotiMCConfig.getInstance();
            boolean isAdvanced = config != null && config.isAdvancedMode();

            // Search & Playlist menu key
            while (searchMenuKey.consumeClick()) {
                if (isAdvanced) {
                    mc.setScreenAndShow(
                            new SpotiMCSearchScreen(mc.gui != null ? mc.gui.screen() : null)
                    );
                } else if (mc.player != null) {
                    mc.player.sendOverlayMessage(
                            Component.literal("Spotify Search requires Advanced Mode (Spotify Premium)")
                    );
                }
            }

            // Playback control keybinds
            if (isAdvanced && SpotiMCClient.SPOTIFY_AUTH != null && SpotiMCClient.SPOTIFY_AUTH.isAuthenticated()) {
                while (nextTrackKey.consumeClick()) {
                    SpotiMCClient.SPOTIFY_API.nextTrack();
                }

                while (previousTrackKey.consumeClick()) {
                    SpotiMCClient.SPOTIFY_API.previousTrack();
                }

                while (playPauseKey.consumeClick()) {
                    SpotiMCClient.SPOTIFY_API.togglePlayPause();
                }
            } else {
                // In Basic Mode or unauthenticated Advanced Mode, notify user on control keypress
                while (nextTrackKey.consumeClick() || previousTrackKey.consumeClick() || playPauseKey.consumeClick()) {
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(
                                Component.literal("Playback controls require Advanced Mode (Spotify Premium)")
                        );
                    }
                }
            }
        });
    }
}
