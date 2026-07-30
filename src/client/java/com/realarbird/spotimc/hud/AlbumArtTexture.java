package com.realarbird.spotimc.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages downloading and caching album art from Spotify or Last.fm as Minecraft textures.
 * Downloads happen asynchronously with timeouts to avoid blocking the game thread.
 */
public class AlbumArtTexture {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/AlbumArt");

    private final ConcurrentHashMap<String, Identifier> textureCache = new ConcurrentHashMap<>();
    private final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private final List<DynamicTexture> registeredTextures = new ArrayList<>();
    private final HttpClient httpClient;

    public AlbumArtTexture() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Gets the texture Identifier for a given album art URL.
     * If the texture is not yet cached, starts an async download and returns null.
     *
     * @param url the album art image URL
     * @return the texture Identifier if cached, or null if still loading
     */
    public Identifier getTexture(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        Identifier cached = textureCache.get(url);
        if (cached != null) {
            return cached;
        }

        // Start async download if not already in progress
        if (downloading.add(url)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "SpotiMC/1.0 (Minecraft Fabric Mod)")
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                            try {
                                byte[] bytes = response.body();
                                NativeImage image = NativeImage.read(bytes);
                                String hash = md5Hash(url);
                                Identifier id = Identifier.fromNamespaceAndPath("spotimc", "albumart/" + hash);

                                // Register on the main thread and upload pixels to GPU
                                Minecraft client = Minecraft.getInstance();
                                if (client != null) {
                                    client.execute(() -> {
                                        try {
                                            DynamicTexture texture = new DynamicTexture(() -> "spotimc_art_" + hash, image);
                                            texture.upload();
                                            client.getTextureManager().register(id, texture);
                                            registeredTextures.add(texture);
                                            textureCache.put(url, id);
                                        } catch (Exception ex) {
                                            LOGGER.error("Failed to register dynamic texture for {}", url, ex);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                LOGGER.error("Failed to read downloaded album art from {}", url, e);
                            }
                        } else {
                            LOGGER.error("Failed to download album art from {} - HTTP {}", url, response.statusCode());
                        }
                    }).exceptionally(ex -> {
                        LOGGER.error("Error downloading album art from {}", url, ex);
                        return null;
                    }).whenComplete((res, ex) -> downloading.remove(url));
        }

        return null;
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
        });
    }
}
