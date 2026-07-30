package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.SpotiMCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Configuration screen for SpotiMC.
 *
 * <p>Provides controls for HUD visibility, scale, Spotify connection,
 * and a draggable HUD preview to reposition the overlay.</p>
 */
public class SpotiMCConfigScreen extends Screen {

    private final Screen parent;
    private SpotiMCConfig config;

    private boolean isDraggingHud = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public SpotiMCConfigScreen(Screen parent) {
        super(Component.literal("SpotiMC Settings"));
        this.parent = parent;
        this.config = SpotiMCConfig.getInstance();
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 40;

        // HUD Visibility toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("HUD Visible: " + (config.hudVisible ? "ON" : "OFF")),
                button -> {
                    config.hudVisible = !config.hudVisible;
                    button.setMessage(Component.literal("HUD Visible: " + (config.hudVisible ? "ON" : "OFF")));
                }
        ).bounds(center - 100, y, 200, 20).build());

        y += 24;

        // HUD Scale (cycling through presets)
        this.addRenderableWidget(Button.builder(
                Component.literal("HUD Scale: " + config.hudScale + "x"),
                button -> {
                    if (config.hudScale == 0.5f) config.hudScale = 0.75f;
                    else if (config.hudScale == 0.75f) config.hudScale = 1.0f;
                    else if (config.hudScale == 1.0f) config.hudScale = 1.25f;
                    else if (config.hudScale == 1.25f) config.hudScale = 1.5f;
                    else if (config.hudScale == 1.5f) config.hudScale = 2.0f;
                    else config.hudScale = 0.5f;
                    button.setMessage(Component.literal("HUD Scale: " + config.hudScale + "x"));
                }
        ).bounds(center - 100, y, 200, 20).build());

        y += 24;

        // Spotify Connect/Reconnect
        boolean authed = SpotiMCClient.SPOTIFY_AUTH != null && SpotiMCClient.SPOTIFY_AUTH.isAuthenticated();
        Component connectText = authed
                ? Component.literal("Reconnect to Spotify")
                : Component.literal("Connect to Spotify");
        this.addRenderableWidget(Button.builder(
                connectText,
                button -> {
                    SpotiMCClient.SPOTIFY_AUTH.startAuth();
                    this.minecraft.setScreenAndShow(null);
                }
        ).bounds(center - 100, y, 200, 20).build());

        y += 24;

        // Reset HUD Position
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset HUD Position"),
                button -> {
                    config.hudX = 10;
                    config.hudY = 10;
                }
        ).bounds(center - 100, y, 200, 20).build());

        // Done button at bottom
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        // Title
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // Connection status
        boolean authed = SpotiMCClient.SPOTIFY_AUTH != null && SpotiMCClient.SPOTIFY_AUTH.isAuthenticated();
        Component statusText = Component.literal(authed ? "Status: Connected" : "Status: Not Connected");
        int statusColor = authed ? 0x1DB954 : 0xFF5555;
        gfx.centeredText(this.font, statusText, this.width / 2, this.height - 50, statusColor);

        // Draw HUD preview for drag-to-reposition
        if (config.hudVisible) {
            gfx.pose().pushMatrix();
            gfx.pose().translate(config.hudX, config.hudY);
            gfx.pose().scale(config.hudScale, config.hudScale);

            gfx.fill(0, 0, 180, 50, 0xCC1A1A2E);
            gfx.textRenderer().accept(10, 20, Component.literal("Drag to move HUD"));

            gfx.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (config.hudVisible && event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            double scaledW = 180 * config.hudScale;
            double scaledH = 50 * config.hudScale;

            if (mouseX >= config.hudX && mouseX <= config.hudX + scaledW
                    && mouseY >= config.hudY && mouseY <= config.hudY + scaledH) {
                isDraggingHud = true;
                dragOffsetX = (int) (mouseX - config.hudX);
                dragOffsetY = (int) (mouseY - config.hudY);
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDraggingHud) {
            config.hudX = (int) (event.x() - dragOffsetX);
            config.hudY = (int) (event.y() - dragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && isDraggingHud) {
            isDraggingHud = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        SpotiMCConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
