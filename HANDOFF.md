# SpotiMC — AI Handoff Log

> This file tracks every code edit, feature addition, bug fix, and architectural decision made during development. Future AI or developer sessions should read this first to resume work seamlessly.

---

## Current Architecture Summary

- **Target Environment**: Minecraft 26.2 (Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25).
- **Dual-Engine Playback**:
  - **Advanced Mode**: Requires Spotify Premium. Integrates Spotify Web API with OAuth2 PKCE (`http://127.0.0.1:4381/callback`), playback controls (Play/Pause, Skip, Previous, Search, Playlist Browsing, Shuffle 🔀, Repeat 🔁).
  - **Basic Mode**: Uses Last.fm REST API (`user.getrecenttracks`). Free to use, read-only track display (no playback controls).
- **Social Overhead Track Display**:
  - Broadcasts song updates via Fabric C2S/S2C custom network payloads (`spotimc:song_update`).
  - Injects into player rendering (`EntityRendererMixin`) to render `🎵 Track Name - Artist Name` in green (`#1DB954`) above player nametags in 3D world space.
- **Non-Blocking Asynchronous Design**:
  - All HTTP clients and requests use strict 5-second connection & request timeouts (`Duration.ofSeconds(5)`).
  - Token refresh and API polling run off the main thread to prevent Minecraft client freezes.

---

## Step-by-Step Setup Guides

### Advanced Mode (Requires Spotify Premium)
1. In-game, press `;` (default SpotiMC key) and select **`Mode: ADVANCED (Requires Spotify Premium)`**.
2. Click **`How to Get API Keys (Setup Guide)...`** and click **`Open Spotify Developer Dashboard`** (or visit [https://developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)).
3. Log in with your Spotify account and click **Create App**.
4. Enter any App Name and App Description (e.g. `My SpotiMC App`).
5. Click **`Copy Callback URL`** in SpotiMC and paste into Redirect URIs (`http://127.0.0.1:4381/callback`).
6. Checkmark **Web API** under APIs used and click **Save**.
7. In your app dashboard, copy your **Client ID** and **Client Secret**.
8. Paste your **Client ID** and **Client Secret** into the in-game SpotiMC text fields and click **Connect to Spotify**.

### Basic Mode (Free - Read-Only Song Display)
1. In-game, press `;` (default SpotiMC key) and select **`Mode: BASIC (Free)`**.
2. Click **`How to Get API Keys (Setup Guide)...`** and click **`Open Last.fm API Key Page`** (or visit [https://www.last.fm/api/account/create](https://www.last.fm/api/account/create)).
3. Log in to Last.fm and fill in any **Application Name** & **Application Description** (e.g. `My SpotiMC App`).
4. Click **`Copy Callback URL`** in SpotiMC and paste into Callback URL (`http://127.0.0.1:4381/callback`).
5. Leave the **Application homepage** section blank.
6. Submit the form to generate your free **API Key**.
7. Copy your **API Key** and **Last.fm Username** into the in-game text fields.
8. Click **Save & Connect Last.fm**.

---

## Chronological Session Logs

### Session 5 — 2026-07-30 (Album Art Blit Fix, Playlist Expansion, Repeat/Shuffle & Social Privacy Controls)
- **Album Cover Rendering Fix**:
  - *Root Cause*: `gfx.blit(artId, 9, 9, 32, 32, 0f, 0f, 1f, 1f)` in `SpotiMCHud.java` passed 1f for region width/height. In Minecraft 26.2 `GuiGraphics`, region dimensions are texel counts, causing Minecraft to stretch a single top-left texel across the 32x32 frame.
  - *Fix*: Updated `SpotiMCHud.java` to use `gfx.blit(artId, 9, 9, 0, 0, 32, 32, 32, 32)` for both album art and default fallback covers.
- **Spotify Search & Status Text Fix**:
  - *Text Visibility*: Updated text color integers in `SpotiMCSearchScreen.java` to 32-bit ARGB with explicit `0xFF` alpha (e.g. `0xFFFFFFFF`, `0xFF1DB954`).
  - *Keyboard Listener*: Added `KeyEvent` handler to `SpotiMCSearchScreen.java` so pressing `Enter` while typing in the search box triggers `performSearch()` immediately.
- **Expanded Playlists, Song Listing, Repeat & Shuffle Controls**:
  - *Playlist Track Listing*: Clicking any playlist in `SpotiMCSearchScreen` opens a Playlist Detail View showing all tracks with title and artist.
  - *Track Playback*: Clicking any song in a playlist plays that specific track in context using `SpotifyAPI.playTrackInContext()`.
  - *Shuffle Control*: Added **🔀 Shuffle (ON / OFF)** button linked to Spotify API (`/me/player/shuffle?state=true|false`).
  - *Repeat Control*: Added **🔁 Repeat (OFF / ALL / ONE)** button linked to Spotify API (`/me/player/repeat?state=off|context|track`).
  - *Track Count Fix*: Updated `getUserPlaylists()` and added `getPlaylistTracks(playlistId)` in `SpotifyAPI.java`.
- **Social Features & Privacy Settings**:
  - *Config Fields*: Added `showOthersListeningStats` (visual toggle for client) and `shareMyListeningStats` (network broadcast privacy toggle) to `SpotiMCConfig.java`.
  - *Social Settings Screen (`SpotiMCSocialConfigScreen.java`)*: Accessible via `Social Features...` button in `SpotiMCConfigScreen.java`.
  - *Network Privacy*: `ClientSongTracker.java` checks `config.shareMyListeningStats`. When OFF, local song payload is not broadcast to the server.
  - *Visual Toggle*: `EntityRendererMixin.java` checks `config.showOthersListeningStats`. When OFF, overhead track tags above other players' heads are hidden.

---

### Session 4 — 2026-07-30 (Basic/Advanced Modes, Last.fm, Social Feature & Anti-Freeze Protection)
- **Basic Mode (Last.fm) & Advanced Mode (Spotify) Dual Engine**: Added `Mode` enum (`BASIC`, `ADVANCED`) in `SpotiMCConfig.java`. UI displays mode tags `Mode: ADVANCED (Requires Spotify Premium)` vs `Mode: BASIC (Free)`.
- **Dedicated Setup Guide Menu (`SpotiMCSetupGuideScreen.java`)**: Added dedicated sub-screen menu displaying step-by-step setup instructions and a direct browser launcher button for Spotify or Last.fm.
- **Click-to-Copy Callback URL Button**: Added interactive **`Copy Callback URL: http://127.0.0.1:4381/callback`** button with clipboard copying and visual feedback (`Copied Callback URL to Clipboard! ✓`).
- **Anti-Freeze Protection & Non-Blocking Async Network Calls**: Replaced blocking `.join()` in `SpotifyAuth.java` with non-blocking `refreshTokenAsync()`. Added strict 5-second connection and request timeouts (`Duration.ofSeconds(5)`) across `SpotifyAuth`, `SpotifyAPI`, `LastFmAPI`, and `AlbumArtTexture`.

---

### Session 3 — 2026-07-30 (Security Hardening & Secret Protection)
- **OAuth2 PKCE Security Migration**: Migrated `SpotifyAuth.java` to use **OAuth2 PKCE (Proof Key for Code Exchange RFC 7636)** with SHA-256 code challenge generation (`code_challenge_method=S256`).
- **No Hardcoded Secrets**: Removed hardcoded client secrets from Java source files. Loaded dynamically from `.minecraft/config/spotimc.json`.
- **Git Secret Prevention**: Updated `.gitignore` with `*.env`, `*.secret`, `spotimc.local.json`, and `run/`. Cleaned commit history.

---

### Session 2 — 2026-07-30 (Cover Art Fix, Text Truncation, Keybind Update & Search Menu)
- **Cover Picture Upload Fix**: Added `texture.upload()` before `TextureManager.register()` in `AlbumArtTexture.java`.
- **Song Title & Artist Truncation (Ellipsis)**: Added `SpotiMCHud.trimToWidth()` helper for 120px max text width.
- **Keybinding Changes**: Play/Pause remapped to **`K`** (`GLFW_KEY_K`), Search Menu mapped to **`O`** (`GLFW_KEY_O`).
- **Spotify Search & Playlists Menu**: Created `SpotiMCSearchScreen.java` for searching tracks and browsing playlists.

---

### Session 1 — 2026-07-30 (Initial Scaffolding & Minecraft 26.2 Modernization)
- Scaffolded Fabric mod project targeting Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25.
- OAuth2 Authorization Code Flow (`http://127.0.0.1:4381/callback`).
- HUD overlay with progress bar in Spotify Green (`#1DB954`).

---

## Build Verification & Repository Status

- **Build Command**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew build
  ```
- **Built Artifact**: `build/libs/spotimc-1.0.0.jar`
- **GitHub Repository**: [https://github.com/realarbird/SpotiMC](https://github.com/realarbird/SpotiMC) (`main` branch)
