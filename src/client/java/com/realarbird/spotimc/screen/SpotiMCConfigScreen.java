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
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * In-game configuration screen for SpotiMC.
 * Supports mode switching (Basic vs Advanced), custom Spotify/Last.fm API credential setup,
 * interactive API key acquisition buttons, HUD customization, and keybind navigation.
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

        y += 24;

        if (config.isAdvancedMode()) {
            // Advanced Mode: Spotify Setup
            this.addRenderableWidget(Button.builder(
                    Component.literal("Get API Keys (Open Spotify Dashboard)"),
                    button -> openUrl("https://developer.spotify.com/dashboard")
            ).bounds(center - 120, y, 240, 20).build());

            y += 42; // Space for instructions below button

            clientIdBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Client ID"));
            clientIdBox.setHint(Component.literal("Client ID"));
            clientIdBox.setMaxLength(128);
            clientIdBox.setValue(config.clientId != null ? config.clientId : "");
            this.addRenderableWidget(clientIdBox);

            y += 22;

            clientSecretBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Client Secret"));
            clientSecretBox.setHint(Component.literal("Client Secret"));
            clientSecretBox.setMaxLength(128);
            clientSecretBox.setValue(config.clientSecret != null ? config.clientSecret : "");
            this.addRenderableWidget(clientSecretBox);

            y += 22;

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
            ).bounds(center - 100, y, 200, 20).build());

            y += 24;

        } else {
            // Basic Mode: Last.fm Setup
            this.addRenderableWidget(Button.builder(
                    Component.literal("Get Last.fm API Key (Free)"),
                    button -> openUrl("https://www.last.fm/api/account/create")
            ).bounds(center - 120, y, 240, 20).build());

            y += 34; // Space for instructions below button

            lastFmApiKeyBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Last.fm API Key"));
            lastFmApiKeyBox.setHint(Component.literal("Last.fm API Key"));
            lastFmApiKeyBox.setMaxLength(128);
            lastFmApiKeyBox.setValue(config.lastFmApiKey != null ? config.lastFmApiKey : "");
            this.addRenderableWidget(lastFmApiKeyBox);

            y += 22;

            lastFmUsernameBox = new EditBox(this.font, center - 100, y, 200, 18, Component.literal("Last.fm Username"));
            lastFmUsernameBox.setHint(Component.literal("Last.fm Username"));
            lastFmUsernameBox.setMaxLength(128);
            lastFmUsernameBox.setValue(config.lastFmUsername != null ? config.lastFmUsername : "");
            this.addRenderableWidget(lastFmUsernameBox);

            y += 22;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Save & Connect Last.fm"),
                    button -> {
                        saveInputFields();
                        SpotiMCClient.updateActiveMode();
                    }
            ).bounds(center - 100, y, 200, 20).build());

            y += 24;
        }

        // Common HUD & Keybind Controls
        this.addRenderableWidget(Button.builder(
                Component.literal("HUD Visible: " + (config.hudVisible ? "ON" : "OFF")),
                button -> {
                    config.hudVisible = !config.hudVisible;
                    button.setMessage(Component.literal("HUD Visible: " + (config.hudVisible ? "ON" : "OFF")));
                    SpotiMCConfig.save();
                }
        ).bounds(center - 100, y, 98, 20).build());

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
        ).bounds(center + 2, y, 98, 20).build());

        y += 22;

        this.addRenderableWidget(Button.builder(
                Component.literal("Configure Keybinds..."),
                button -> {
                    saveInputFields();
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new KeyBindsScreen(this, this.minecraft.options));
                    }
                }
        ).bounds(center - 100, y, 98, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Reset HUD Pos"),
                button -> config.resetHudPosition()
        ).bounds(center + 2, y, 98, 20).build());

        // Done button
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 25, 200, 20).build());
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("SpotiMC").error("Failed to open URL in web browser: {}", url, e);
        }
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

        // Title
        gfx.centeredText(this.font, this.title, center, 10, 0xFFFFFF);

        if (config.isAdvancedMode()) {
            // Instructions for Spotify Advanced Mode
            int instY = 72;
            gfx.centeredText(this.font, Component.literal("1. Create App -> Redirect URI: http://127.0.0.1:4381/callback"), center, instY, 0xFFD700);
            gfx.centeredText(this.font, Component.literal("2. Checkmark \"Web API\" -> Paste Client ID & Secret below:"), center, instY + 10, 0xAAAAAA);
        } else {
            // Instructions for Last.fm Basic Mode
            int instY = 72;
            gfx.centeredText(this.font, Component.literal("1. Create account & app to get your free API Key"), center, instY, 0xAAAAAA);
            gfx.centeredText(this.font, Component.literal("2. Enter API Key & Username below:"), center, instY + 10, 0xFFD700);
        }

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
