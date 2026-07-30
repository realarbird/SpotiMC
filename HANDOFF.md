# SpotiMC — AI Handoff Log

> This file tracks every code edit, feature addition, bug fix, and architectural decision made during development. Future AI or developer sessions should read this first to resume work seamlessly.

---

## Session 4 — 2026-07-30 (Basic/Advanced Modes, Last.fm, Social Feature, Album Cover Fix & Custom Keys)

### What Was Done

#### 1. Basic Mode (Last.fm) & Advanced Mode (Spotify) Dual Engine
- **Mode Switch**: Added `Mode` enum (`BASIC`, `ADVANCED`) in `SpotiMCConfig.java`.
- **Mode Labels**: Config UI clearly displays `Mode: ADVANCED (Requires Spotify Premium)` when Advanced Mode is selected and `Mode: BASIC (Free)` when Basic Mode is selected.
- **Interactive API Key Buttons**:
  - Advanced Mode: `Get API Keys (Open Spotify Dashboard)` button launches `https://developer.spotify.com/dashboard` directly in the user's web browser.
  - Basic Mode: `Get Last.fm API Key (Free)` button launches `https://www.last.fm/api/account/create` directly in the user's web browser.

#### Step-by-Step Setup Instructions

##### Advanced Mode (Requires Spotify Premium)
1. Open SpotiMC settings (Default key: `;`) and select **`Mode: ADVANCED (Requires Spotify Premium)`**.
2. Click **`Get API Keys (Open Spotify Dashboard)`** or visit [https://developer.spotify.com/dashboard](https://developer.spotify.com/dashboard).
3. Log in with your Spotify account and click **Create App**.
4. Enter any App Name and App Description (e.g. `My SpotiMC App`).
5. Set Redirect URI to exactly: `http://127.0.0.1:4381/callback`
6. Checkmark **Web API** under APIs used and click **Save**.
7. In your app dashboard, copy your **Client ID** and **Client Secret**.
8. Paste your **Client ID** and **Client Secret** into the in-game SpotiMC text fields and click **Connect to Spotify**.

##### Basic Mode (Free - Read-Only Song Display)
1. Open SpotiMC settings (Default key: `;`) and select **`Mode: BASIC (Free)`**.
2. Click **`Get Last.fm API Key (Free)`** or visit [https://www.last.fm/api/account/create](https://www.last.fm/api/account/create).
3. Log in to Last.fm and fill in any **Application Name** & **Application Description** (e.g. `My SpotiMC App`).
4. Set **Callback URL** to: `http://127.0.0.1:4381/callback`.
5. Leave the **Application homepage** section blank.
6. Submit the form to generate your free **API Key**.
7. Copy your **API Key** and **Last.fm Username** into the in-game text fields.
8. Click **Save & Connect Last.fm**.

#### 2. Album Cover Rendering Fix
- **Root Cause**: HTTP client in `AlbumArtTexture.java` was missing HTTP redirect handling (`Redirect.ALWAYS`) and custom `User-Agent` headers, causing CDN requests to fail with 301/302 or 403 status codes.
- **Fix**: Updated `AlbumArtTexture.java` with `.followRedirects(HttpClient.Redirect.ALWAYS)` and `User-Agent: SpotiMC/1.0 (Minecraft Fabric Mod)`. Stream data is read completely into byte arrays before `NativeImage.read()` decoding.

#### 3. Social Feature (Overhead Song Rendering)
- **Networking Payload**: Created `SpotiMCSongPayload.java` (Identifier `spotimc:song_update`).
- **Server Broadcasting**: Common entrypoint `SpotiMCMod.java` registers C2S and S2C payload channels and broadcasts song updates to all connected players.
- **Client Tracking**: `ClientSongTracker.java` stores overhead track info and broadcasts client song state.
- **Overhead Renderer**: Added `EntityRendererMixin.java` injecting into `EntityRenderer.extractNameTags`. Renders `🎵 Track Name - Artist Name` in green (`#1DB954`) above player nametags in both Basic and Advanced modes.

#### 4. Custom Keybindings & Overlay Scoping
- **Rebindable Keybinds**: Keybinds are registered with Fabric and configurable via Minecraft's standard Controls -> Key Binds menu (accessible directly from `SpotiMCConfigScreen`).
- **Basic Mode Overlay Notice**: Attempting to use playback controls (Play/Pause, Next, Previous, Search) in Basic Mode triggers an action bar message: `"Playback controls require Advanced Mode (Spotify Premium)"`.

#### 5. GUI Redesign & Mod Icon Update
- **Config GUI (`SpotiMCConfigScreen.java`)**: Added mode toggle button, web browser launch buttons for API keys, step-by-step instructions, text boxes for credentials, and keybind settings button.
- **Mod Icon**: Updated `src/main/resources/assets/spotimc/icon.png` using `/Users/ayanraj/Documents/SpotiMC/SpotiMC.png`.

---

## Session 3 — 2026-07-30 (Security Hardening & Secret Protection)

### What Was Done

#### 1. OAuth2 PKCE Security Migration
- **PKCE Implementation**: Migrated `SpotifyAuth.java` to use **OAuth2 PKCE (Proof Key for Code Exchange RFC 7636)** with SHA-256 code challenge generation (`code_challenge_method=S256`).
- **No Hardcoded Secrets**: Removed all hardcoded client secret constants from Java source files and documentation to comply with GitGuardian secret detection policies.
- **Dynamic Local Config**: Added `clientId` and `clientSecret` fields to `SpotiMCConfig.java`. Credentials are dynamically loaded at runtime from `.minecraft/config/spotimc.json`.

#### 2. Git Secret Prevention & `.gitignore` Updates
- Added patterns to `.gitignore`: `*.env`, `*.secret`, `spotimc.local.json`, and `run/`.
- Configured local development run directory (`run/config/spotimc.json`) for uncommitted local key storage.

#### 3. Repository Cleansing
- Scrubbed historical commits containing secrets and force-pushed clean tree to GitHub (`https://github.com/realarbird/SpotiMC`).
- Latest commit: `26c8953`.

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

## Build Verification & Push Status
- `./gradlew build` verified successful (`build/libs/spotimc-1.0.0.jar`).
- Pushed to GitHub repository: `https://github.com/realarbird/SpotiMC`.
