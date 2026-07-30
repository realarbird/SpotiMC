# SpotiMC — AI Handoff Log

> This file tracks every code edit, feature addition, bug fix, and architectural decision made during development. Future AI or developer sessions should read this first to resume work seamlessly.

---

## Session 2 — 2026-07-30 (Cover Art Fix, Text Truncation, Keybind Update & Search Menu)

### What Was Done

#### 1. Cover Picture & GPU Texture Upload Fix
- **Root Cause**: `DynamicTexture` registration was creating a texture instance without calling `texture.upload()`, causing pixel data to remain un-uploaded to GPU memory.
- **Fix**: Added `texture.upload()` before `TextureManager.register()` in `AlbumArtTexture.java`.
- **Default Cover**: Added `default_cover.png` fallback in `src/main/resources/assets/spotimc/textures/gui/default_cover.png` and mod `icon.png`.
- **Primary Image**: Updated `PlaybackState.java` to pick `images.get(0)` (the main high-resolution cover image) from Spotify API.

#### 2. Song Title & Artist Truncation (Ellipsis)
- Added `SpotiMCHud.trimToWidth(Font font, String text, int maxPixelWidth)` helper.
- Automatically calculates rendered text width against the HUD's 120px max text width.
- If track title or artist name exceeds 120px, it is trimmed and appended with `...` to prevent HUD text overflow.

#### 3. Keybinding Changes
- **Play / Pause**: Changed default key mapping from `P` to **`K`** (`GLFW_KEY_K`) to prevent conflicts with standard game controls.
- **New Search & Playlist Menu Keybind**: Registered `key.spotimc.search_menu` mapped to **`O`** (`GLFW_KEY_O`).

#### 4. Spotify Search & Playlists Menu (`screen/SpotiMCSearchScreen.java`)
- Added interactive in-game Spotify search screen allowing users to:
  - 🔍 Search tracks by title/artist directly from Spotify.
  - 🎵 Browse and select from user's personal playlists.
  - Click any track/playlist result to play it instantly.
- Added API helper methods in `SpotifyAPI.java`: `searchTracks()`, `getUserPlaylists()`, `playTrackUri()`, `playContextUri()`.
- Added `playlist-read-private` and `playlist-read-collaborative` OAuth scopes in `SpotifyAuth.java`.

---

## Session 1 — 2026-07-30 (Initial Scaffolding & Minecraft 26.2 Modernization)

### What Was Done
- Scaffolded Fabric mod project targeting **Minecraft 26.2**, **Fabric Loader 0.19.3**, **Fabric API 0.155.2+26.2**, **Java 25**.
- OAuth2 Authorization Code Flow (`http://127.0.0.1:4381/callback`).
- HUD overlay with progress bar in Spotify Green (`#1DB954`).
- Config Screen with HUD drag-to-reposition and scale controls.

---

## Spotify OAuth Setup & PKCE Authentication
- Uses PKCE (Proof Key for Code Exchange) flow — no client secrets required.
- **Redirect URI**: `http://127.0.0.1:4381/callback`

---

## Build Verification & Push Status
- `./gradlew build` verified successful (`build/libs/spotimc-1.0.0.jar`).
- Pushed to GitHub repository: `https://github.com/realarbird/SpotiMC`.
