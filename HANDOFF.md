# SpotiMC — AI Handoff Log

> This file tracks every code edit, feature addition, bug fix, and architectural decision made during development. Future AI or developer sessions should read this first to resume work seamlessly.

---

## Session 5 — 2026-07-30 (Album Art Blit Fix, Playlist Expansion, Repeat/Shuffle & Social Privacy Controls)

### What Was Done

#### 1. Album Cover Rendering Fix
- **Root Cause**: `gfx.blit(artId, 9, 9, 32, 32, 0f, 0f, 1f, 1f)` in `SpotiMCHud.java` passed 1f for region width/height. In Minecraft 26.2 `GuiGraphics`, region dimensions are texel counts, causing Minecraft to stretch a single top-left texel across the 32x32 frame.
- **Fix**: Updated `SpotiMCHud.java` to use `gfx.blit(artId, 9, 9, 0, 0, 32, 32, 32, 32)` for both album art and default fallback covers, properly displaying full cover images.

#### 2. Spotify Search & Status Text Fix
- **Text Visibility**: Updated text color integers in `SpotiMCSearchScreen.java` to 32-bit ARGB with explicit `0xFF` alpha (e.g. `0xFFFFFFFF`, `0xFF1DB954`).
- **Keyboard Listener**: Added `KeyEvent` handler to `SpotiMCSearchScreen.java` so pressing `Enter` while typing in the search box triggers `performSearch()` immediately.

#### 3. Expanded Playlists, Song Listing, Repeat & Shuffle Controls
- **Playlist Track Listing**: Clicking any playlist in `SpotiMCSearchScreen` opens a Playlist Detail View showing all tracks with title and artist.
- **Track Playback**: Clicking any song in a playlist plays that specific track in context using `SpotifyAPI.playTrackInContext()`.
- **Shuffle Control**: Added **🔀 Shuffle (ON / OFF)** button linked to Spotify API (`/me/player/shuffle?state=true|false`).
- **Repeat Control**: Added **🔁 Repeat (OFF / ALL / ONE)** button linked to Spotify API (`/me/player/repeat?state=off|context|track`).
- **Track Count Fix**: Updated `getUserPlaylists()` and added `getPlaylistTracks(playlistId)` in `SpotifyAPI.java`.

#### 4. Social Features & Privacy Settings
- **Config Fields**: Added `showOthersListeningStats` (visual toggle for client) and `shareMyListeningStats` (network broadcast privacy toggle) to `SpotiMCConfig.java`.
- **Social Settings Screen (`SpotiMCSocialConfigScreen.java`)**: Accessible via `Social Features...` button in `SpotiMCConfigScreen.java`.
- **Network Privacy**: `ClientSongTracker.java` checks `config.shareMyListeningStats`. When OFF, local song payload is not broadcast to the server.
- **Visual Toggle**: `EntityRendererMixin.java` checks `config.showOthersListeningStats`. When OFF, overhead track tags above other players' heads are hidden.

---

## Session 4 — 2026-07-30 (Basic/Advanced Modes, Last.fm, Social Feature, Album Cover Fix & Custom Keys)

### What Was Done

#### 1. Basic Mode (Last.fm) & Advanced Mode (Spotify) Dual Engine
- **Mode Switch**: Added `Mode` enum (`BASIC`, `ADVANCED`) in `SpotiMCConfig.java`.
- **Mode Labels**: Config UI clearly displays `Mode: ADVANCED (Requires Spotify Premium)` when Advanced Mode is selected and `Mode: BASIC (Free)` when Basic Mode is selected.
- **Dedicated Setup Guide Menu (`SpotiMCSetupGuideScreen.java`)**:
  - `How to Get API Keys (Setup Guide)...` button opens a clean sub-screen menu displaying step-by-step setup instructions and a direct browser launcher button for Spotify (`https://developer.spotify.com/dashboard`) or Last.fm (`https://www.last.fm/api/account/create`).
  - Added interactive **`Copy Callback URL: http://127.0.0.1:4381/callback`** button that copies the callback/redirect URL directly to system clipboard when clicked and provides instant visual feedback (`Copied Callback URL to Clipboard! ✓`).
- **Text Visibility Fix**: Fixed text color format in `SpotiMCSetupGuideScreen.java` to use 32-bit ARGB with explicit `0xFF` alpha prefix (e.g. `0xFFFFFFFF`, `0xFF1DB954`, `0xFFDDDDDD`). Rendered a dark card background container (`0xEE12121E`) behind instruction text for 100% crystal-clear readability.

---

## Build Verification & Push Status
- `./gradlew build` verified successful (`build/libs/spotimc-1.0.0.jar`).
- Pushed to GitHub repository: `https://github.com/realarbird/SpotiMC`.
