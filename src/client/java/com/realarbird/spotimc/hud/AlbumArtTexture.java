package com.realarbird.spotimc.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages downloading and caching album art from Spotify or Last.fm as Minecraft textures.
 * Downloads happen asynchronously on a worker thread with timeouts to avoid blocking
 * the game thread.
 */
public class AlbumArtTexture {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/AlbumArt");

    /** Cached texture identifiers keyed by image URL. */
    private final ConcurrentHashMap<String, CachedTexture> textureCache = new ConcurrentHashMap<>();
    private final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();
    private final List<DynamicTexture> registeredTextures = new ArrayList<>();
    private final HttpClient httpClient;

    /** Stores a cached texture's Identifier and its pixel dimensions. */
    public record CachedTexture(Identifier id, int width, int height) {}

    public AlbumArtTexture() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public boolean isFailed(String url) {
        return url != null && failedUrls.contains(url);
    }

    public boolean isDownloading(String url) {
        return url != null && downloading.contains(url);
    }

    /**
     * Gets the cached texture info for a given album art URL.
     * If the texture is not yet cached, starts an async download and returns null.
     *
     * @param url the album art image URL
     * @return the CachedTexture if cached, or null if still loading or failed
     */
    public CachedTexture getCachedTexture(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        CachedTexture cached = textureCache.get(url);
        if (cached != null) {
            return cached;
        }

        if (failedUrls.contains(url)) {
            return null;
        }

        // Start async download if not already in progress
        if (downloading.add(url)) {
            LOGGER.info("[AlbumArtTexture] Starting async album art download from URL: {}", url);

            // Use CompletableFuture.runAsync to perform the blocking HTTP
            // download on a worker thread. This avoids potential issues
            // with HttpClient.sendAsync's internal executor inside the
            // Minecraft JVM.
            CompletableFuture.runAsync(() -> downloadAndRegister(url));
        }

        return null;
    }

    /**
     * Downloads an image from the given URL, decodes it to a NativeImage, and
     * registers it as a DynamicTexture on the main client thread.
     * This method runs on a worker thread.
     */
    private void downloadAndRegister(String url) {
        try {
            LOGGER.info("[AlbumArtTexture] Sending HTTP GET for album art: {}", url);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "SpotiMC/1.0 (Minecraft Fabric Mod)")
                    .GET()
                    .build();

            // Blocking send on the worker thread — keeps the async logic simple.
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            String contentType = response.headers().firstValue("content-type").orElse("unknown");
            int bodyLen = response.body() != null ? response.body().length : 0;

            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                LOGGER.error("[AlbumArtTexture] FAILED to download album art from {} — HTTP Status: {} (content-type: {}, body length: {} bytes)",
                        url, response.statusCode(), contentType, bodyLen);
                failedUrls.add(url);
                downloading.remove(url);
                return;
            }

            LOGGER.info("[AlbumArtTexture] Successfully downloaded album art ({} bytes, content-type: {}) from {}", bodyLen, contentType, url);

            // Decode the image bytes. NativeImage.read(InputStream) requests RGBA
            // from STB Image, so the result is always RGBA regardless of source format.
            NativeImage image;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body())) {
                image = NativeImage.read(bais);
            } catch (Exception decodeEx) {
                LOGGER.error("[AlbumArtTexture] FAILED to decode image bytes for URL: {} (byte count: {})", url, bodyLen, decodeEx);
                failedUrls.add(url);
                downloading.remove(url);
                return;
            }

            int imgW = image.getWidth();
            int imgH = image.getHeight();
            LOGGER.info("[AlbumArtTexture] Decoded album art dimensions: {}x{}, format: {} for URL: {}", imgW, imgH, image.format(), url);

            String hash = md5Hash(url);
            Identifier id = Identifier.fromNamespaceAndPath("spotimc", "albumart/" + hash);

            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                LOGGER.warn("[AlbumArtTexture] Minecraft client instance is null, cannot register texture for {}", url);
                image.close();
                downloading.remove(url);
                return;
            }

            // Schedule texture registration on the main render/client thread.
            client.execute(() -> {
                try {
                    // The DynamicTexture constructor calls createTexture() to allocate
                    // a GpuTexture, then upload() to push pixel data. Both require
                    // the render thread, which client.execute() guarantees.
                    DynamicTexture texture = new DynamicTexture(() -> "spotimc_art_" + hash, image);
                    client.getTextureManager().register(id, texture);
                    registeredTextures.add(texture);
                    CachedTexture ct = new CachedTexture(id, imgW, imgH);
                    textureCache.put(url, ct);
                    LOGGER.info("[AlbumArtTexture] SUCCESSFULLY registered texture identifier {} ({}x{}) for URL: {}", id, imgW, imgH, url);
                } catch (Exception ex) {
                    LOGGER.error("[AlbumArtTexture] FAILED to register dynamic texture on main thread for URL: {}", url, ex);
                    failedUrls.add(url);
                    image.close();
                } finally {
                    downloading.remove(url);
                }
            });

        } catch (Exception e) {
            LOGGER.error("[AlbumArtTexture] Network or process error downloading album art from URL: {}", url, e);
            failedUrls.add(url);
            downloading.remove(url);
        }
    }

    /**
     * Backward-compatible convenience: returns just the texture Identifier.
     */
    public Identifier getTexture(String url) {
        CachedTexture ct = getCachedTexture(url);
        return ct != null ? ct.id() : null;
    }

    /**
     * Computes an MD5 hash of the input string, used to generate unique texture IDs.
     */
    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }

    /**
     * Destroys all registered textures and clears the cache.
     */
    public void close() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            for (DynamicTexture texture : registeredTextures) {
                texture.close();
            }
            registeredTextures.clear();
            textureCache.clear();
            failedUrls.clear();
            downloading.clear();
        });
    }
}
