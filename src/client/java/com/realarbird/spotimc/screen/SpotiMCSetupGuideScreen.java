package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * Dedicated setup guide sub-screen providing clear, step-by-step instructions
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
        int y = 40;

        if (mode == SpotiMCConfig.Mode.ADVANCED) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Open Spotify Developer Dashboard"),
                    button -> openUrl("https://developer.spotify.com/dashboard")
            ).bounds(center - 120, y, 240, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(
                    Component.literal("Open Last.fm API Key Page"),
                    button -> openUrl("https://www.last.fm/api/account/create")
            ).bounds(center - 120, y, 240, 20).build());
        }

        // Back button at bottom
        this.addRenderableWidget(Button.builder(
                Component.literal("Back to Settings"),
                button -> this.onClose()
        ).bounds(center - 100, this.height - 30, 200, 20).build());
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

        // Title
        gfx.centeredText(this.font, this.title, center, 15, 0xFFFFFF);

        int textY = 75;
        int lineGap = 14;

        if (mode == SpotiMCConfig.Mode.ADVANCED) {
            gfx.centeredText(this.font, Component.literal("Spotify Setup (Requires Spotify Premium)"), center, textY, 0x1DB954);
            textY += lineGap + 4;
            gfx.centeredText(this.font, Component.literal("1. Click button above to open Spotify Developer Dashboard."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("2. Log in and click 'Create App'."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("3. Fill in any App Name & Description (e.g. My SpotiMC App)."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("4. Add Redirect URI: http://127.0.0.1:4381/callback"), center, textY, 0xFFD700);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("5. Checkmark \"Web API\" under APIs used and click Save."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("6. Go to Settings and copy your Client ID and Client Secret."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("7. Paste them into SpotiMC Settings and click Connect."), center, textY, 0xDDDDDD);
        } else {
            gfx.centeredText(this.font, Component.literal("Last.fm Setup (Free - Read-Only Song Display)"), center, textY, 0x1DB954);
            textY += lineGap + 4;
            gfx.centeredText(this.font, Component.literal("1. Click button above to open Last.fm API Key creation page."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("2. Log in to Last.fm and fill out App Name & Description."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("3. Set Callback URL: http://127.0.0.1:4381/callback"), center, textY, 0xFFD700);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("4. Leave the Application Homepage field BLANK."), center, textY, 0xFF5555);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("5. Submit the form to generate your free API Key."), center, textY, 0xDDDDDD);
            textY += lineGap;
            gfx.centeredText(this.font, Component.literal("6. Copy your API Key & Last.fm Username into SpotiMC Settings."), center, textY, 0xDDDDDD);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
