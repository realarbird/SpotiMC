package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings menu for configuring Social Features (Overhead track display privacy controls).
 */
public class SpotiMCSocialConfigScreen extends Screen {

    private final Screen parent;
    private final SpotiMCConfig config;

    public SpotiMCSocialConfigScreen(Screen parent) {
        super(Component.literal("Social Features Settings"));
        this.parent = parent;
        this.config = SpotiMCConfig.getInstance();
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 45;

        // 1. Show Others' Songs Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Show Other Players' Songs: " + (config.showOthersListeningStats ? "ON" : "OFF")),
                button -> {
                    config.showOthersListeningStats = !config.showOthersListeningStats;
                    button.setMessage(Component.literal("Show Other Players' Songs: " + (config.showOthersListeningStats ? "ON" : "OFF")));
                    SpotiMCConfig.save();
                }
        ).bounds(center - 130, y, 260, 20).build());

        y += 50;

        // 2. Share My Song Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Share My Song With Others: " + (config.shareMyListeningStats ? "ON" : "OFF")),
                button -> {
                    config.shareMyListeningStats = !config.shareMyListeningStats;
                    button.setMessage(Component.literal("Share My Song With Others: " + (config.shareMyListeningStats ? "ON" : "OFF")));
                    SpotiMCConfig.save();
                }
        ).bounds(center - 130, y, 260, 20).build());

        // Back button
        this.addRenderableWidget(Button.builder(
                Component.literal("Back to Settings"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        int center = this.width / 2;

        // Header Title (Opaque White 0xFFFFFFFF)
        gfx.centeredText(this.font, this.title, center, 15, 0xFFFFFFFF);

        // Subtitle explanations for options
        gfx.centeredText(this.font, Component.literal("Visual: Controls whether overhead songs are visible on other players."), center, 69, 0xFFAAAAAA);

        gfx.centeredText(this.font, Component.literal("Privacy: Controls whether your playing song is sent to other players."), center, 119, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
