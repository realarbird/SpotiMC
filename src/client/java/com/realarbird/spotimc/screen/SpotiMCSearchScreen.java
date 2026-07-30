package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.spotify.SpotifyAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Spotify Menu allowing users to search tracks and switch between their playlists.
 */
public class SpotiMCSearchScreen extends Screen {

    private final Screen parent;
    private boolean isSearchTab = true;

    // Search UI
    private EditBox searchBox;
    private final List<SpotifyAPI.TrackSearchResult> trackResults = new ArrayList<>();
    private final List<Button> trackResultButtons = new ArrayList<>();

    // Playlists UI
    private final List<SpotifyAPI.PlaylistSearchResult> playlistResults = new ArrayList<>();
    private final List<Button> playlistResultButtons = new ArrayList<>();

    private String statusMessage = "";

    public SpotiMCSearchScreen(Screen parent) {
        super(Component.literal("Spotify Library & Search"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        // Tab Toggle Buttons
        this.addRenderableWidget(Button.builder(
                Component.literal("🔍 Search Songs"),
                b -> {
                    isSearchTab = true;
                    rebuildTabWidgets();
                }
        ).bounds(center - 110, 35, 105, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("🎵 My Playlists"),
                b -> {
                    isSearchTab = false;
                    rebuildTabWidgets();
                    fetchPlaylists();
                }
        ).bounds(center + 5, 35, 105, 20).build());

        // Back / Close Button at bottom
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                b -> this.onClose()
        ).bounds(center - 100, this.height - 28, 200, 20).build());

        rebuildTabWidgets();
    }

    private void rebuildTabWidgets() {
        // Remove existing result buttons & search box
        trackResultButtons.forEach(this::removeWidget);
        playlistResultButtons.forEach(this::removeWidget);
        trackResultButtons.clear();
        playlistResultButtons.clear();

        if (searchBox != null) {
            this.removeWidget(searchBox);
        }

        int center = this.width / 2;

        if (isSearchTab) {
            // Search Input Box
            searchBox = new EditBox(this.font, center - 130, 65, 200, 20, Component.literal("Search..."));
            searchBox.setMaxLength(100);
            this.addRenderableWidget(searchBox);

            // Search Trigger Button
            this.addRenderableWidget(Button.builder(
                    Component.literal("Search"),
                    b -> performSearch()
            ).bounds(center + 75, 65, 55, 20).build());

            // Build buttons for track results
            int y = 92;
            Font font = Minecraft.getInstance().font;
            for (SpotifyAPI.TrackSearchResult track : trackResults) {
                if (y + 20 > this.height - 35) break;

                String label = track.name() + " - " + track.artistName();
                if (font.width(label) > 240) {
                    label = font.plainSubstrByWidth(label, 230) + "...";
                }

                Button btn = Button.builder(
                        Component.literal(label),
                        b -> {
                            if (SpotiMCClient.SPOTIFY_API != null) {
                                SpotiMCClient.SPOTIFY_API.playTrackUri(track.uri());
                                statusMessage = "Playing: " + track.name();
                            }
                        }
                ).bounds(center - 130, y, 260, 18).build();

                trackResultButtons.add(btn);
                this.addRenderableWidget(btn);
                y += 20;
            }
        } else {
            // Playlist Buttons
            int y = 65;
            Font font = Minecraft.getInstance().font;
            for (SpotifyAPI.PlaylistSearchResult playlist : playlistResults) {
                if (y + 20 > this.height - 35) break;

                String label = playlist.name() + " (" + playlist.trackCount() + " tracks)";
                if (font.width(label) > 240) {
                    label = font.plainSubstrByWidth(label, 230) + "...";
                }

                Button btn = Button.builder(
                        Component.literal(label),
                        b -> {
                            if (SpotiMCClient.SPOTIFY_API != null) {
                                SpotiMCClient.SPOTIFY_API.playContextUri(playlist.uri());
                                statusMessage = "Playing Playlist: " + playlist.name();
                            }
                        }
                ).bounds(center - 130, y, 260, 18).build();

                playlistResultButtons.add(btn);
                this.addRenderableWidget(btn);
                y += 20;
            }
        }
    }

    private void performSearch() {
        if (searchBox == null || SpotiMCClient.SPOTIFY_API == null) return;
        String query = searchBox.getValue();
        if (query.trim().isEmpty()) return;

        statusMessage = "Searching...";
        SpotiMCClient.SPOTIFY_API.searchTracks(query).thenAccept(results -> {
            Minecraft.getInstance().execute(() -> {
                trackResults.clear();
                trackResults.addAll(results);
                statusMessage = results.isEmpty() ? "No songs found" : "Found " + results.size() + " songs";
                rebuildTabWidgets();
            });
        });
    }

    private void fetchPlaylists() {
        if (SpotiMCClient.SPOTIFY_API == null) return;
        statusMessage = "Loading playlists...";
        SpotiMCClient.SPOTIFY_API.getUserPlaylists().thenAccept(results -> {
            Minecraft.getInstance().execute(() -> {
                playlistResults.clear();
                playlistResults.addAll(results);
                statusMessage = results.isEmpty() ? "No playlists found" : "Loaded " + results.size() + " playlists";
                rebuildTabWidgets();
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        // Header Title
        gfx.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Status text at bottom
        if (!statusMessage.isEmpty()) {
            gfx.centeredText(this.font, Component.literal(statusMessage), this.width / 2, this.height - 42, 0x1DB954);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
