package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.SpotiMCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * In-game configuration screen for SpotiMC.
 * Supports mode switching (Basic vs Advanced), custom Spotify/Last.fm API credential setup,
 * setup guide navigation, HUD customization, keybind configuration, and social privacy controls.
 */
public class SpotiMCConfigScreen extends Screen {

    private final Screen parent;
    private final SpotiMCConfig config;

    private EditBox clientIdBox;
    private EditBox clientSecretBox;
    private EditBox lastFmApiKeyBox;
    private EditBox lastFmUsernameBox;

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
        int y = 25;

        // 1. Mode Switcher Button with clear mode indicators
        String modeLabel = config.isAdvancedMode()
                ? "Mode: ADVANCED (Requires Spotify Premium)"
                : "Mode: BASIC (Free)";

        this.addRenderableWidget(Button.builder(
                Component.literal(modeLabel),
                button -> {
                    config.mode = config.isAdvancedMode() ? SpotiMCConfig.Mode.BASIC : SpotiMCConfig.Mode.ADVANCED;
                    SpotiMCConfig.save();
                    SpotiMCClient.updateActiveMode();
                    this.rebuildWidgets();
                }
        ).bounds(center - 120, y, 240, 20).build());

        y += 22;

        // 2. Setup Guide Button that opens the sub-screen with step-by-step instructions and web link
        this.addRenderableWidget(Button.builder(
                Component.literal("How to Get API Keys (Setup Guide)..."),
                button -> {
                    saveInputFields();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new SpotiMCSetupGuideScreen(this, config.mode));
                    }
                }
        ).bounds(center - 120, y, 240, 20).build());

        y += 24;

        if (config.isAdvancedMode()) {
            // Advanced Mode Inputs (Spotify)
            clientIdBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Client ID"));
            clientIdBox.setHint(Component.literal("Client ID"));
            clientIdBox.setMaxLength(128);
            clientIdBox.setValue(config.clientId != null ? config.clientId : "");
            this.addRenderableWidget(clientIdBox);

            y += 20;

            clientSecretBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Client Secret"));
            clientSecretBox.setHint(Component.literal("Client Secret"));
            clientSecretBox.setMaxLength(128);
            clientSecretBox.setValue(config.clientSecret != null ? config.clientSecret : "");
            this.addRenderableWidget(clientSecretBox);

            y += 20;

            boolean authed = SpotiMCClient.SPOTIFY_AUTH != null && SpotiMCClient.SPOTIFY_AUTH.isAuthenticated();
            Component connectText = authed ? Component.literal("Reconnect to Spotify") : Component.literal("Connect to Spotify");
            this.addRenderableWidget(Button.builder(
                    connectText,
                    button -> {
                        saveInputFields();
                        SpotiMCClient.SPOTIFY_AUTH.startAuth();
                        if (this.minecraft != null) {
                            this.minecraft.setScreenAndShow(null);
                        }
                    }
            ).bounds(center - 100, y, 200, 18).build());

            y += 22;

        } else {
            // Basic Mode Inputs (Last.fm)
            lastFmApiKeyBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Last.fm API Key"));
            lastFmApiKeyBox.setHint(Component.literal("Last.fm API Key"));
            lastFmApiKeyBox.setMaxLength(128);
            lastFmApiKeyBox.setValue(config.lastFmApiKey != null ? config.lastFmApiKey : "");
            this.addRenderableWidget(lastFmApiKeyBox);

            y += 20;

            lastFmUsernameBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Last.fm Username"));
            lastFmUsernameBox.setHint(Component.literal("Last.fm Username"));
            lastFmUsernameBox.setMaxLength(128);
            lastFmUsernameBox.setValue(config.lastFmUsername != null ? config.lastFmUsername : "");
            this.addRenderableWidget(lastFmUsernameBox);

            y += 20;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Save & Connect Last.fm"),
                    button -> {
                        saveInputFields();
                        SpotiMCClient.updateActiveMode();
                    }
            ).bounds(center - 100, y, 200, 18).build());

            y += 22;
        }

        // Common HUD & Keybind Controls
        this.addRenderableWidget(Button.builder(
                Component.literal("HUD: " + (config.hudVisible ? "ON" : "OFF")),
                button -> {
                    config.hudVisible = !config.hudVisible;
                    button.setMessage(Component.literal("HUD: " + (config.hudVisible ? "ON" : "OFF")));
                    SpotiMCConfig.save();
                }
        ).bounds(center - 100, y, 98, 18).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Scale: " + config.hudScale + "x"),
                button -> {
                    if (config.hudScale == 0.5f) config.hudScale = 0.75f;
                    else if (config.hudScale == 0.75f) config.hudScale = 1.0f;
                    else if (config.hudScale == 1.0f) config.hudScale = 1.25f;
                    else if (config.hudScale == 1.25f) config.hudScale = 1.5f;
                    else if (config.hudScale == 1.5f) config.hudScale = 2.0f;
                    else config.hudScale = 0.5f;
                    button.setMessage(Component.literal("Scale: " + config.hudScale + "x"));
                    SpotiMCConfig.save();
                }
        ).bounds(center + 2, y, 98, 18).build());

        y += 20;

        this.addRenderableWidget(Button.builder(
                Component.literal("Keybinds..."),
                button -> {
                    saveInputFields();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
                    }
                }
        ).bounds(center - 100, y, 98, 18).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Reset HUD"),
                button -> config.resetHudPosition()
        ).bounds(center + 2, y, 98, 18).build());

        y += 20;

        // Social Features Settings Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Social Features..."),
                button -> {
                    saveInputFields();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new SpotiMCSocialConfigScreen(this));
                    }
                }
        ).bounds(center - 100, y, 200, 18).build());

        // Done button
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 24, 200, 20).build());
    }

    private void saveInputFields() {
        if (clientIdBox != null) config.clientId = clientIdBox.getValue().trim();
        if (clientSecretBox != null) config.clientSecret = clientSecretBox.getValue().trim();
        if (lastFmApiKeyBox != null) config.lastFmApiKey = lastFmApiKeyBox.getValue().trim();
        if (lastFmUsernameBox != null) config.lastFmUsername = lastFmUsernameBox.getValue().trim();
        SpotiMCConfig.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        int center = this.width / 2;

        // Title (Opaque White)
        gfx.centeredText(this.font, this.title, center, 8, 0xFFFFFFFF);

        // HUD Preview for dragging
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
        saveInputFields();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
