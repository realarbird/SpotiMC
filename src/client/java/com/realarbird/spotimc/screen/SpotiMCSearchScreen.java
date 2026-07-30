package com.realarbird.spotimc.screen;

import com.realarbird.spotimc.SpotiMCClient;
import com.realarbird.spotimc.spotify.PlaybackState;
import com.realarbird.spotimc.spotify.SpotifyAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Spotify Library & Search Screen with full track search, playlist song listing,
 * playback controls, shuffle, and repeat toggles.
 */
public class SpotiMCSearchScreen extends Screen {

    private final Screen parent;
    
    public enum Tab { SEARCH, PLAYLISTS, PLAYLIST_DETAIL }
    private Tab currentTab = Tab.SEARCH;

    // Search UI
    private EditBox searchBox;
    private Button searchButton;
    private final List<SpotifyAPI.TrackSearchResult> trackResults = new ArrayList<>();
    private final List<Button> trackResultButtons = new ArrayList<>();

    // Playlists UI
    private final List<SpotifyAPI.PlaylistSearchResult> playlistResults = new ArrayList<>();
    private final List<Button> playlistResultButtons = new ArrayList<>();

    // Playlist Detail UI
    private SpotifyAPI.PlaylistSearchResult selectedPlaylist;
    private final List<SpotifyAPI.TrackSearchResult> playlistTrackResults = new ArrayList<>();
    private final List<Button> playlistTrackButtons = new ArrayList<>();
    private final List<Button> detailHeaderButtons = new ArrayList<>();

    private String statusMessage = "";

    public SpotiMCSearchScreen(Screen parent) {
        super(Component.literal("Spotify Search & Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;

        // Navigation Tabs
        this.addRenderableWidget(Button.builder(
                Component.literal("🔍 Search Songs"),
                b -> {
                    currentTab = Tab.SEARCH;
                    rebuildTabWidgets();
                }
        ).bounds(center - 110, 32, 105, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("🎵 My Playlists"),
                b -> {
                    currentTab = Tab.PLAYLISTS;
                    rebuildTabWidgets();
                    fetchPlaylists();
                }
        ).bounds(center + 5, 32, 105, 20).build());

        // Bottom Done Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                b -> this.onClose()
        ).bounds(center - 100, this.height - 26, 200, 20).build());

        rebuildTabWidgets();
    }

    private void rebuildTabWidgets() {
        trackResultButtons.forEach(this::removeWidget);
        playlistResultButtons.forEach(this::removeWidget);
        playlistTrackButtons.forEach(this::removeWidget);
        detailHeaderButtons.forEach(this::removeWidget);
        trackResultButtons.clear();
        playlistResultButtons.clear();
        playlistTrackButtons.clear();
        detailHeaderButtons.clear();

        if (searchBox != null) {
            this.removeWidget(searchBox);
            searchBox = null;
        }
        if (searchButton != null) {
            this.removeWidget(searchButton);
            searchButton = null;
        }

        int center = this.width / 2;
        Font font = Minecraft.getInstance().font;

        if (currentTab == Tab.SEARCH) {
            // Search Input Box
            searchBox = new EditBox(font, center - 130, 58, 195, 18, Component.literal("Search..."));
            searchBox.setHint(Component.literal("Type song title or artist..."));
            searchBox.setMaxLength(100);
            this.addRenderableWidget(searchBox);

            // Search Trigger Button
            searchButton = Button.builder(
                    Component.literal("Search"),
                    b -> performSearch()
            ).bounds(center + 70, 58, 60, 18).build();
            this.addRenderableWidget(searchButton);

            // Track Result Buttons
            int y = 82;
            for (SpotifyAPI.TrackSearchResult track : trackResults) {
                if (y + 18 > this.height - 32) break;

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
                y += 19;
            }
        } else if (currentTab == Tab.PLAYLISTS) {
            // Playlists List
            int y = 58;
            for (SpotifyAPI.PlaylistSearchResult playlist : playlistResults) {
                if (y + 18 > this.height - 32) break;

                String countText = playlist.trackCount() > 0 ? playlist.trackCount() + " tracks" : "Open Playlist";
                String label = playlist.name() + " (" + countText + ")";
                if (font.width(label) > 240) {
                    label = font.plainSubstrByWidth(label, 230) + "...";
                }

                Button btn = Button.builder(
                        Component.literal(label),
                        b -> openPlaylistDetail(playlist)
                ).bounds(center - 130, y, 260, 18).build();

                playlistResultButtons.add(btn);
                this.addRenderableWidget(btn);
                y += 19;
            }
        } else if (currentTab == Tab.PLAYLIST_DETAIL && selectedPlaylist != null) {
            // Playlist Header Controls (Row 1: Back & Play Playlist)
            PlaybackState playback = SpotiMCClient.SPOTIFY_API != null ? SpotiMCClient.SPOTIFY_API.getCurrentPlayback() : PlaybackState.EMPTY;
            String shuffleLabel = "🔀 Shuffle: " + (playback.shuffleState() ? "ON" : "OFF");
            
            String repeatLabel = "🔁 Repeat: OFF";
            if ("track".equalsIgnoreCase(playback.repeatState())) repeatLabel = "🔁 Repeat: ONE";
            else if ("context".equalsIgnoreCase(playback.repeatState())) repeatLabel = "🔁 Repeat: ALL";

            Button backBtn = Button.builder(
                    Component.literal("◀ Back"),
                    b -> {
                        currentTab = Tab.PLAYLISTS;
                        rebuildTabWidgets();
                    }
            ).bounds(center - 130, 56, 55, 18).build();
            detailHeaderButtons.add(backBtn);
            this.addRenderableWidget(backBtn);

            Button playPlaylistBtn = Button.builder(
                    Component.literal("▶ Play Playlist"),
                    b -> {
                        if (SpotiMCClient.SPOTIFY_API != null) {
                            SpotiMCClient.SPOTIFY_API.playContextUri(selectedPlaylist.uri());
                            statusMessage = "Playing Playlist: " + selectedPlaylist.name();
                        }
                    }
            ).bounds(center - 70, 56, 200, 18).build();
            detailHeaderButtons.add(playPlaylistBtn);
            this.addRenderableWidget(playPlaylistBtn);

            // Row 2: Shuffle & Repeat Toggles
            Button shuffleBtn = Button.builder(
                    Component.literal(shuffleLabel),
                    b -> {
                        if (SpotiMCClient.SPOTIFY_API != null) {
                            SpotiMCClient.SPOTIFY_API.setShuffle(!playback.shuffleState());
                            rebuildTabWidgets();
                        }
                    }
            ).bounds(center - 130, 78, 125, 18).build();
            detailHeaderButtons.add(shuffleBtn);
            this.addRenderableWidget(shuffleBtn);

            String finalRepeatLabel = repeatLabel;
            Button repeatBtn = Button.builder(
                    Component.literal(finalRepeatLabel),
                    b -> {
                        if (SpotiMCClient.SPOTIFY_API != null) {
                            String nextRepeat = "off";
                            if ("off".equalsIgnoreCase(playback.repeatState())) nextRepeat = "context";
                            else if ("context".equalsIgnoreCase(playback.repeatState())) nextRepeat = "track";
                            SpotiMCClient.SPOTIFY_API.setRepeat(nextRepeat);
                            rebuildTabWidgets();
                        }
                    }
            ).bounds(center + 5, 78, 125, 18).build();
            detailHeaderButtons.add(repeatBtn);
            this.addRenderableWidget(repeatBtn);

            // Playlist Track Buttons starting at y = 100
            int y = 100;
            for (SpotifyAPI.TrackSearchResult track : playlistTrackResults) {
                if (y + 18 > this.height - 32) break;

                String label = track.name() + " - " + track.artistName();
                if (font.width(label) > 240) {
                    label = font.plainSubstrByWidth(label, 230) + "...";
                }

                Button btn = Button.builder(
                        Component.literal(label),
                        b -> {
                            if (SpotiMCClient.SPOTIFY_API != null) {
                                SpotiMCClient.SPOTIFY_API.playTrackInContext(selectedPlaylist.uri(), track.uri());
                                statusMessage = "Playing: " + track.name();
                            }
                        }
                ).bounds(center - 130, y, 260, 18).build();

                playlistTrackButtons.add(btn);
                this.addRenderableWidget(btn);
                y += 19;
            }
        }
    }

    private void performSearch() {
        if (searchBox == null || SpotiMCClient.SPOTIFY_API == null) return;
        String query = searchBox.getValue();
        if (query.trim().isEmpty()) return;

        statusMessage = "Searching...";
        SpotiMCClient.SPOTIFY_API.searchTracks(query).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                trackResults.clear();
                trackResults.addAll(result.items());
                statusMessage = result.succeeded()
                        ? (result.items().isEmpty() ? "No songs found" : "Found " + result.items().size() + " songs")
                        : result.errorMessage();
                rebuildTabWidgets();
            });
        });
    }

    private void fetchPlaylists() {
        if (SpotiMCClient.SPOTIFY_API == null) return;
        statusMessage = "Loading playlists...";
        SpotiMCClient.SPOTIFY_API.getUserPlaylists().thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                playlistResults.clear();
                playlistResults.addAll(result.items());
                statusMessage = result.succeeded()
                        ? (result.items().isEmpty() ? "No playlists found" : "Loaded " + result.items().size() + " playlists")
                        : result.errorMessage();
                rebuildTabWidgets();
            });
        });
    }

    private void openPlaylistDetail(SpotifyAPI.PlaylistSearchResult playlist) {
        if (SpotiMCClient.SPOTIFY_API == null || playlist == null) return;
        this.selectedPlaylist = playlist;
        this.currentTab = Tab.PLAYLIST_DETAIL;
        statusMessage = "Loading tracks for " + playlist.name() + "...";
        rebuildTabWidgets();

        SpotiMCClient.SPOTIFY_API.getPlaylistTracks(playlist.id()).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                playlistTrackResults.clear();
                playlistTrackResults.addAll(result.items());
                statusMessage = result.succeeded()
                        ? (result.items().isEmpty() ? "No tracks found in playlist" : playlist.name() + " (" + result.items().size() + " tracks)")
                        : result.errorMessage();
                rebuildTabWidgets();
            });
        });
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (currentTab == Tab.SEARCH && searchBox != null && searchBox.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                performSearch();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);

        // Header Title (Opaque White 0xFFFFFFFF)
        gfx.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Status text at bottom (Opaque Green 0xFF1DB954)
        if (!statusMessage.isEmpty()) {
            gfx.centeredText(this.font, Component.literal(statusMessage), this.width / 2, this.height - 38, 0xFF1DB954);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
