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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages downloading and caching album art from Spotify or Last.fm as Minecraft textures.
 * Downloads happen asynchronously with timeouts to avoid blocking the game thread.
 */
public class AlbumArtTexture {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/AlbumArt");

    private final ConcurrentHashMap<String, Identifier> textureCache = new ConcurrentHashMap<>();
    private final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();
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
     * @return the texture Identifier if cached, or null if still loading or failed
     */
    public Identifier getTexture(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Identifier cached = textureCache.get(url);
        if (cached != null) {
            return cached;
        }

        if (failedUrls.contains(url)) {
            return null;
        }

        // Start async download if not already in progress
        if (downloading.add(url)) {
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "SpotiMC/1.0 (Minecraft Fabric Mod)")
                        .GET()
                        .build();
            } catch (Exception e) {
                LOGGER.error("Invalid album art URL: {}", url, e);
                failedUrls.add(url);
                downloading.remove(url);
                return null;
            }

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                            try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body())) {
                                NativeImage loaded = NativeImage.read(bais);
                                int srcW = loaded.getWidth();
                                int srcH = loaded.getHeight();

                                NativeImage image = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
                                loaded.resizeSubRectTo(0, 0, srcW, srcH, image);
                                loaded.close();

                                String hash = md5Hash(url);
                                Identifier id = Identifier.fromNamespaceAndPath("spotimc", "albumart/" + hash);

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
                                            failedUrls.add(url);
                                            image.close();
                                        } finally {
                                            downloading.remove(url);
                                        }
                                    });
                                } else {
                                    image.close();
                                    downloading.remove(url);
                                }
                            } catch (Exception e) {
                                LOGGER.error("Failed to decode downloaded album art from {}", url, e);
                                failedUrls.add(url);
                                downloading.remove(url);
                            }
                        } else {
                            LOGGER.error("Failed to download album art from {} - HTTP {}", url, response.statusCode());
                            failedUrls.add(url);
                            downloading.remove(url);
                        }
                    }).exceptionally(ex -> {
                        LOGGER.error("Error downloading album art from {}", url, ex);
                        failedUrls.add(url);
                        downloading.remove(url);
                        return null;
                    });
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
            failedUrls.clear();
            downloading.clear();
        });
    }
}
