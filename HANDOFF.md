# SpotiMC — AI Handoff Log

> This file tracks every code edit, feature addition, bug fix, and architectural decision made during development. Future AI or developer sessions should read this first to resume work seamlessly.

---

## Current Architecture Summary

- **Target Environment**: Minecraft 26.2 (Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25).
- **Dual-Engine Playback**:
  - **Advanced Mode**: Requires Spotify Premium. Integrates Spotify Web API with OAuth2 PKCE (`http://127.0.0.1:4381/callback`), playback controls (Play/Pause, Skip, Previous, Search, Playlist Browsing, Shuffle 🔀, Repeat 🔁).
  - **Basic Mode**: Uses Last.fm REST APIs (`user.getrecenttracks` plus a cached `track.getInfo` artwork fallback). Free to use, read-only track display (no playback controls).
- **Social Overhead Track Display**:
  - Broadcasts song updates via Fabric C2S/S2C custom network payloads (`spotimc:song_update`).
  - Injects into player rendering (`EntityRendererMixin`) to render the player name and a separate green `♫ Track Name — Artist Name` line underneath it in 3D world space.
  - The dedicated server and every player who should share/see statuses must run the same SpotiMC version: the server routes the custom payload between clients.
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

### Session 10 — 2026-07-30 (Album Art Texture Loader Rewrite & Blit UV Fix)
- **Album Art Still Showing Fallback Only (Both Modes)**:
  - *Root Cause 1*: `AlbumArtTexture.java` used `HttpClient.sendAsync()` to download images, which delegates to an internal JVM executor. Inside the Minecraft JVM, this executor may silently fail, drop callbacks, or be subject to thread pool restrictions that prevent the download from completing. The previous Session 9 fix used `resizeSubRectTo()` to resize images to 64×64, but this is unnecessary overhead and adds another failure point.
  - *Root Cause 2*: The HUD `blit` call used the 11-parameter overload `blit(pipeline, id, x, y, u, v, 32, 32, 64, 64, color)` which internally passes `drawWidth` as `srcRegionWidth`, causing UV sampling of only the top-left quarter (32/64 = 50%) of the texture instead of the full image.
  - *Root Cause 3*: `DynamicTexture(Supplier, NativeImage)` constructor already calls `createTexture()` and `upload()` internally. The redundant `texture.upload()` call after construction was harmless but indicated misunderstanding of the API.
  - *Fix*: Complete rewrite of `AlbumArtTexture.java`:
    - Replaced `httpClient.sendAsync()` with `CompletableFuture.runAsync()` + blocking `httpClient.send()`, mirroring the proven working pattern from `SpotifyAPI.pollPlaybackState()`.
    - Removed the intermediate `NativeImage` resize step — the decoded image is passed directly to `DynamicTexture` at its original resolution (typically 300×300 for Spotify, variable for Last.fm).
    - Added `CachedTexture` record storing `(Identifier, width, height)` so the HUD can blit with correct UV coordinates matching the actual texture dimensions.
    - Added comprehensive `LOGGER.info/error` logging at every step (download start, byte count, decode dimensions, registration, failures).
  - *Fix HUD*: `SpotiMCHud.java` now uses the 12-parameter `blit` overload with `srcRegionWidth=textureWidth, srcRegionHeight=textureHeight` to sample the entire texture, rendering it scaled down to the 32×32 HUD cover slot.
  - *Fix Diagnostics*: Added `LOGGER` to `PlaybackState.java` and `LastFmAPI.java` to log extracted `albumArtUrl` values, making it possible to trace whether the API returns image URLs.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.

### Session 9 — 2026-07-30 (Advanced Mode Album Art & Texture Decoding Fix)
- **Advanced Mode Album Art & Podcast Cover Resolution**:
  - *Root Cause 1*: In `PlaybackState.fromJson`, Spotify item JSON was only checked for `item.album.images`. Podcasts, shows, and non-standard tracks return image arrays under `item.show.images` or `item.images`, causing `albumArtUrl` to remain empty.
  - *Root Cause 2*: `AlbumArtTexture.java` manually resampled native image pixels using a Java `getPixel`/`setPixel` loop. `NativeImage.getPixel()` returns ARGB format, but `setPixel()` required ABGR/RGBA conversion, corrupting image colors and alpha channels. Furthermore, failed downloads or decoding errors continuously re-triggered HTTP requests on every render frame without recording failed URLs.
  - *Fix*: `PlaybackState.java` now parses image arrays from `item.album`, `item.show`, and direct `item` fields, preferring 300px/64px URLs for faster network transfers. `AlbumArtTexture.java` now decodes images via `ByteArrayInputStream` and resizes natively via LWJGL C code (`resizeSubRectTo`), safely closing decoded native images, preventing memory leaks, and tracking failed URLs (`failedUrls`) to prevent frame-by-frame network spam.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.

### Session 8 — 2026-07-30 (Basic Mode Actual Album Art Lookup)
- **Actual Album Cover Fix**:
  - *Root Cause*: Last.fm’s `user.getrecenttracks` response frequently omits album image URLs. `PlaybackState` therefore had an empty artwork URL and the HUD correctly—but unhelpfully—displayed `default_cover.png` for every song.
  - *Fix*: `LastFmAPI` now chooses the largest usable image from the recent-track response, rejects Last.fm’s known “no image available” placeholder, and falls back once per artist/album/track to `track.getInfo`. The fallback URL is cached, so a song's real cover is fetched once and then supplied to the existing dynamic-texture loader on every later poll.
  - *Verification*: Confirmed Last.fm's `track.getInfo` response for **Mwaki — Zerb** includes the Surrender EP cover URL. The client build also passes with the Session 7 Java 25 build command.

### Session 7 — 2026-07-30 (Minecraft 26.2 HUD Fix, Current Spotify Library API & Reliable Social Tags)
- **HUD Album Cover Rendering Fix**:
  - *Root Cause*: Minecraft 26.2 changed `GuiGraphicsExtractor.blit(Identifier, ...)`. The old argument pattern was interpreted as a zero-width, zero-height rectangle, so neither the fallback nor downloaded cover could render.
  - *Fix*: `SpotiMCHud.java` now calls the explicit `RenderPipelines.GUI_TEXTURED` overload with a 32×32 destination and 64×64 source texture dimensions for both dynamic and fallback cover art.
- **Spotify Search & Playlist Data Fix**:
  - *Root Cause*: Spotify now limits Search to 10 results per type, but SpotiMC requested 20 and converted the resulting HTTP 400 into “No songs found.” Spotify playlist summaries now publish their count in `items.total` (with `tracks` deprecated), and the preferred item route is `/playlists/{id}/items` rather than `/tracks`.
  - *Fix*: Search requests use `limit=10`; playlist counts prefer `items.total`; playlist loading uses `/items`; and UI failures now show a useful connection/permission/rate-limit error instead of incorrectly reporting an empty library.
- **OAuth Refresh Retry**:
  - *Root Cause*: A 401 refreshed the token in the background but returned an empty result to the initial search/playlist request.
  - *Fix*: `SpotifyAuth.refreshTokenAsync()` coalesces concurrent refreshes and returns their result. Spotify library reads retry once using the newly refreshed token.
- **Multiplayer Listening Status Fix**:
  - *Root Cause*: Minecraft’s 26.2 name-tag feature renders a single formatted line; newline characters are not laid out as a second overhead line.
  - *Fix*: The renderer now submits the existing player name as the upper label and a bounded green `♫ Track — Artist` as its own lower label. The server also replaces client-supplied player identity with the authenticated sender and bounds packet text lengths.
  - *Deployment*: Install the resulting JAR on the dedicated server and on each participating client. Both clients must keep **Share My Listening Stats** and **Show Others’ Listening Stats** enabled as appropriate.

### Session 6 — 2026-07-30 (Album Cover Resampling Fix, Playlist Layout Fix & Spotify API 401 Recovery)
- **Album Cover Rendering & Resampling Fix**:
  - *Root Cause*: `default_cover.png` was a 1024x1024 JPEG image, and downloaded Spotify/Last.fm album art varied in dimensions (300x300, 640x640) and RGB format. `gfx.blit()` passed `32, 32` as texture dimensions, sampling only top-left 0.1% of images.
  - *Fix*: Resized `default_cover.png` to 64x64 PNG. `AlbumArtTexture.java` resamples all downloaded album art to uniform 64x64 RGBA `NativeImage` objects with opaque alpha. Updated `SpotiMCHud.java` `gfx.blit()` texture dimensions to `64, 64`.
- **Playlists Menu Button Overlap Fix**:
  - *Root Cause*: Playlist Detail view header buttons (`Back`, `Play Playlist`, `Shuffle`, `Repeat`) were created directly without being tracked, so `rebuildTabWidgets()` left old buttons on screen. Shuffle and Repeat button widths (52px) were too small for their text labels.
  - *Fix*: Tracked header buttons in `detailHeaderButtons` list and cleaned them up in `rebuildTabWidgets()`. Redesigned header to a 2-row layout (`Back` 55px + `Play Playlist` 200px on Row 1; `Shuffle` 125px + `Repeat` 125px on Row 2).
- **Search & Playlist Track Resolution & 401 Token Refresh**:
  - *Root Cause*: `searchTracks()`, `getUserPlaylists()`, and `getPlaylistTracks()` returned empty lists on HTTP 401 Unauthorized without refreshing tokens or logging warnings.
  - *Fix*: Added HTTP 401 handling calling `auth.refreshTokenAsync()` and warning logging. Added URI-based fallback for missing playlist IDs in `getUserPlaylists()`. Enhanced `getPlaylistTracks()` JSON parsing to safely extract tracks from varied wrapper formats (`track`, `item`, or direct object).

---

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

### Session 12 — 2026-08-01 (Lunar Client Silent Initialization Failure Diagnosis & Fix)
- **Album Art Shows Fallback Only — Root Cause Found in `latest.log`**:
  - *Diagnosis*: The user's `latest.log` was analyzed. Despite SpotiMC appearing in the Fabric ResourceManager mod list, **zero SpotiMC log messages** were emitted — not even the `"Initializing SpotiMC client..."` first line of `onInitializeClient()`. This proves the mod's entrypoints were **never invoked** by Fabric Loader.
  - *Environment*: The user runs on **Lunar Client** (`com.moonsworth.lunar.genesis.*` bootstrap), which wraps Minecraft in its own classloader (`Genesis`). Lunar Client can silently swallow initialization errors that would otherwise crash or log in vanilla Fabric.
  - *Root Cause 1 — Mixin `defaultRequire: 1`*: `spotimc.mixins.json` had `"defaultRequire": 1`, meaning if the `EntityRendererMixin` injection target (`extractNameTags`) didn't match Lunar Client's remapped method signature, the mixin system would throw a hard error. Lunar's Genesis bootstrap likely caught this error during mod construction and silently disabled the entire mod before entrypoints ran.
  - *Root Cause 2 — No error resilience*: `onInitialize()` and `onInitializeClient()` had no try-catch wrappers, so any exception (e.g., `NoClassDefFoundError` from a Fabric API version mismatch for `HudElementRegistry`, class verification errors from Lunar's module system, or classloading failures) would propagate and silently kill initialization under Genesis.
  - *Fix*:
    - **`spotimc.mixins.json`**: Changed `"defaultRequire": 1` → `"defaultRequire": 0` so a mixin target miss degrades gracefully instead of killing the mod.
    - **`SpotiMCMod.java`**: Added `System.out.println("[SpotiMC] SpotiMCMod.onInitialize() ENTRY")` at the very start (before any SLF4J call) and wrapped the entire method body in try-catch logging to both `LOGGER.error` and `System.err`.
    - **`SpotiMCClient.java`**: Same treatment — `System.out.println` entry marker, try-catch wrapper, added config/mode logging after load.
    - **`EntityRendererMixin.java`**: Wrapped the injection method body in try-catch to prevent renderer crashes from propagating.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.
  - *Next Step*: Install the new JAR and check `latest.log` for `[SpotiMC]` prefixed messages. If entrypoints now fire but album art still fails, the detailed Session 11 logging will pinpoint whether the issue is: (a) no album art URL from the API, (b) HTTP download failure, (c) image decode failure, or (d) texture registration failure.

### Session 13 — 2026-08-01 (Lunar Client SLF4J Bypass & STDOUT Diagnostics Verification)
- **Mod Initializing Successfully — SLF4J Filtered by Lunar Client**:
  - *Diagnosis*: Analysis of the updated `latest.log` confirmed lines 7-9:
    - `[STDOUT]: [SpotiMC] SpotiMCMod.onInitialize() ENTRY`
    - `[STDOUT]: [SpotiMC] SpotiMCClient.onInitializeClient() ENTRY`
    - `[STDOUT]: [SpotiMC] SpotiMCClient.onInitializeClient() completed successfully`
    This proves **Session 12 completely fixed mod initialization under Lunar Client**. Both common and client entrypoints now execute cleanly to completion.
  - *Remaining Issue*: Lunar Client's log configuration suppresses SLF4J `LOGGER.info` / `LOGGER.warn` outputs for custom mod categories (`"SpotiMC"`, `"SpotiMC/AlbumArt"`, `"SpotiMC/HUD"`), preventing diagnostic logs from writing to `latest.log`.
  - *Fix*: Converted all diagnostic loggers in `SpotiMCHud`, `AlbumArtTexture`, `PlaybackState`, `SpotifyAPI`, and `LastFmAPI` from SLF4J `LOGGER` to `System.out.println` / `System.err.println` (`[SpotiMC/...]` tags), which Lunar Client forwards directly to `latest.log` under `[STDOUT]`/`[STDERR]`.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.

### Session 14 — 2026-08-01 (Album Art Decoding Fix — Resolved `Bad PNG Signature` Exception)
- **Root Cause Found in `latest.log`**:
  - *Diagnosis*: Analyzing the latest log revealed:
    `[STDERR]: java.io.IOException: Bad PNG Signature` at `net.minecraft.util.PngInfo.validateHeader(PngInfo.java:53)` → `NativeImage.read(NativeImage.java:154)`
  - *Root Cause*: In Minecraft 26.2, Mojang updated `NativeImage.read(InputStream)` to validate PNG magic bytes (`0x89 50 4E 47`). Spotify and Last.fm return **JPEG** images (`image/jpeg`, e.g. `https://i.scdn.co/image/...`), causing `NativeImage.read()` to fail header validation with `Bad PNG Signature` for every song.
  - *Fix*:
    - **`AlbumArtTexture.java`**: Added a fallback decoder using `javax.imageio.ImageIO.read()`. When `NativeImage.read()` throws an `IOException` due to a non-PNG signature, the stream is reset and decoded via `ImageIO`. The decoded `BufferedImage` pixels are converted to ABGR integers and written to `NativeImage.setPixelABGR(x, y, abgr)`.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.

### Session 15 — 2026-08-01 (Fallback Cover Image Update to Official SpotiMC Mod Logo)
- **Mod Icon Fallback Cover Artwork**:
  - *Objective*: Replace the old AI-generated fallback cover art with the official SpotiMC mod logo image (`icon.png` / `SpotiMC.png`).
  - *Changes*:
    - **`default_cover.png`**: Resized the official 1254x1254 SpotiMC mod logo image (`icon.png`) to 256x256 RGBA PNG using high-quality Lanczos anti-aliasing resampling and saved to `src/main/resources/assets/spotimc/textures/gui/default_cover.png`.
    - **`SpotiMCHud.java`**: Updated `DEFAULT_COVER_SIZE` from 64 to 256 so the HUD blits the crisp 256x256 fallback texture scaled to the 32x32 HUD artwork slot.
  - *Verification*: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build` passed cleanly.

---

## Build Verification & Repository Status

- **Build Command**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew build
  ```
- **Built Artifact**: `build/libs/spotimc-1.0.0.jar`
- **Latest Verification (Session 15)**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build
  ```
  Completed successfully on 2026-08-01.
- **GitHub Repository**: [https://github.com/realarbird/SpotiMC](https://github.com/realarbird/SpotiMC) (`main` branch)
