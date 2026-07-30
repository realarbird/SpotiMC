package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

/**
 * Dedicated setup guide sub-screen providing clear, step-by-step instructions,
 * an interactive button to copy the redirect/callback URL directly to system clipboard,
 * and a direct web browser link button to get API keys for Spotify or Last.fm.
 */
public class SpotiMCSetupGuideScreen extends Screen {

    private final Screen parent;
    private final SpotiMCConfig.Mode mode;

    public SpotiMCSetupGuideScreen(Screen parent, SpotiMCConfig.Mode mode) {
        super(Component.literal(mode == SpotiMCConfig.Mode.ADVANCED ? "Spotify Setup Guide" : "Last.fm Setup Guide"));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 28;

        // 1. Open Web Page Button
        if (mode == SpotiMCConfig.Mode.ADVANCED) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Open Spotify Developer Dashboard"),
                    button -> openUrl("https://developer.spotify.com/dashboard")
            ).bounds(center - 130, y, 260, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Open Last.fm API Key Page"),
                    button -> openUrl("https://www.last.fm/api/account/create")
            ).bounds(center - 130, y, 260, 20).build());
        }

        y += 24;

        // 2. Click-to-Copy Callback URL Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Copy Callback URL: http://127.0.0.1:4381/callback"),
                button -> {
                    copyToClipboard("http://127.0.0.1:4381/callback");
                    button.setMessage(Component.literal("Copied Callback URL to Clipboard! ✓"));
                }
        ).bounds(center - 140, y, 280, 20).build());

        // Back button at bottom
        this.addRenderableWidget(Button.builder(
                Component.literal("Back to Settings"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 28, 200, 20).build());
    }

    private void copyToClipboard(String text) {
        try {
            if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                this.minecraft.keyboardHandler.setClipboard(text);
            } else {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(text), null
                );
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("SpotiMC").error("Failed to copy to clipboard", e);
        }
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        int center = this.width / 2;

        // Title (Opaque White 0xFFFFFFFF)
        gfx.centeredText(this.font, this.title, center, 10, 0xFFFFFFFF);

        // Dark background card container behind text
        int cardLeft = center - 195;
        int cardRight = center + 195;
        int cardTop = 76;
        int cardBottom = this.height - 34;
        gfx.fill(cardLeft, cardTop, cardRight, cardBottom, 0xEE12121E);

        int textY = 82;
        int lineGap = 13;

        if (mode == SpotiMCConfig.Mode.ADVANCED) {
            gfx.centeredText(this.font, Component.literal("Spotify Setup (Requires Spotify Premium)"), center, textY, 0xFF1DB954);
            textY += lineGap + 3;
            gfx.centeredText(this.font, Component.literal("1. Click button above to open Spotify Developer Dashboard."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("2. Log in and click 'Create App'."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("3. Fill in any App Name & Description (e.g. My SpotiMC App)."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("4. Click 'Copy Callback URL' above and paste in Redirect URIs."), center, textY, 0xFFFFD700);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("5. Checkmark \"Web API\" under APIs used and click Save."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("6. Go to Settings and copy your Client ID and Client Secret."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("7. Paste them into SpotiMC Settings and click Connect."), center, textY, 0xFFDDDDDD);
        } else {
            gfx.centeredText(this.font, Component.literal("Last.fm Setup (Free - Read-Only Song Display)"), center, textY, 0xFF1DB954);
            textY += lineGap + 3;
            gfx.centeredText(this.font, Component.literal("1. Click button above to open Last.fm API Key creation page."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("2. Log in to Last.fm and fill out App Name & Description."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("3. Click 'Copy Callback URL' above and paste in Callback URL."), center, textY, 0xFFFFD700);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("4. Leave the Application Homepage field BLANK."), center, textY, 0xFFFF5555);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("5. Submit the form to generate your free API Key."), center, textY, 0xFFDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("6. Copy your API Key & Last.fm Username into SpotiMC Settings."), center, textY, 0xFFDDDDDD);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
