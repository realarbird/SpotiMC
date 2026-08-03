package com.realarbird.spotimc.social;

import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.network.SpotiMCSongPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player song states on the client and handles broadcasting current player track info over network.
 */
public class ClientSongTracker {

    public record PlayerSongInfo(
            UUID uuid,
            String playerName,
            String trackName,
            String artistName,
            boolean isPlaying
    ) {}

    private static final Map<UUID, PlayerSongInfo> PLAYER_SONGS = new ConcurrentHashMap<>();

    private static long lastBroadcastTime = 0;
    private static String lastSentTrack = null;
    private static String lastSentArtist = null;
    private static boolean lastSentIsPlaying = false;
    private static boolean lastSentShareSetting = true;

    /**
     * Updates song info stored for a player.
     */
    public static void updateSong(SpotiMCSongPayload payload) {
        if (payload == null || payload.playerUuid() == null) return;

        if (payload.isPlaying() && payload.trackName() != null && !payload.trackName().trim().isEmpty()) {
            PLAYER_SONGS.put(payload.playerUuid(), new PlayerSongInfo(
                    payload.playerUuid(),
                    payload.playerName(),
                    payload.trackName(),
                    payload.artistName(),
                    payload.isPlaying()
            ));
        } else {
            PLAYER_SONGS.remove(payload.playerUuid());
        }
    }

    /**
     * Gets current song info for a player UUID.
     */
    public static PlayerSongInfo getSong(UUID playerUuid) {
        if (playerUuid == null) return null;
        return PLAYER_SONGS.get(playerUuid);
    }

    /**
     * Clears all stored remote player song statuses (e.g. on server disconnect).
     */
    public static void clearAll() {
        PLAYER_SONGS.clear();
        lastBroadcastTime = 0;
        lastSentTrack = null;
        lastSentArtist = null;
        lastSentIsPlaying = false;
    }

    /**
     * Forces immediate broadcast of current player status (e.g. on server join).
     */
    public static void forceBroadcast(String trackName, String artistName, boolean isPlaying) {
        lastBroadcastTime = 0;
        lastSentTrack = null;
        lastSentArtist = null;
        lastSentIsPlaying = !isPlaying; // force mismatch
        tickBroadcast(trackName, artistName, isPlaying);
    }

    /**
     * Periodically sends local player's song update packet to server/peers.
     * Respects user privacy settings (shareMyListeningStats).
     * Immediately broadcasts when song or privacy state changes.
     */
    public static void tickBroadcast(String trackName, String artistName, boolean isPlaying) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SpotiMCConfig config = SpotiMCConfig.getInstance();
        boolean share = config != null && config.shareMyListeningStats;

        String effectiveTrack = (share && trackName != null) ? trackName : "";
        String effectiveArtist = (share && artistName != null) ? artistName : "";
        boolean effectiveIsPlaying = share && isPlaying;

        boolean stateChanged = !Objects.equals(effectiveTrack, lastSentTrack)
                || !Objects.equals(effectiveArtist, lastSentArtist)
                || effectiveIsPlaying != lastSentIsPlaying
                || share != lastSentShareSetting;

        long now = System.currentTimeMillis();
        // Send immediately if state changed, or every 5000ms as heartbeat
        if (!stateChanged && (now - lastBroadcastTime < 5000)) {
            return;
        }

        lastBroadcastTime = now;
        lastSentTrack = effectiveTrack;
        lastSentArtist = effectiveArtist;
        lastSentIsPlaying = effectiveIsPlaying;
        lastSentShareSetting = share;

        UUID localUuid = mc.player.getUUID();
        String localName = mc.player.getName().getString();

        SpotiMCSongPayload payload = new SpotiMCSongPayload(
                localUuid,
                localName,
                effectiveTrack,
                effectiveArtist,
                effectiveIsPlaying
        );

        // Update locally for singleplayer / local overhead view
        updateSong(payload);

        // Send to server if connected
        try {
            if (ClientPlayNetworking.canSend(SpotiMCSongPayload.TYPE)) {
                ClientPlayNetworking.send(payload);
            }
        } catch (Exception e) {
            System.err.println("[SpotiMC/Social] Failed to send song payload: " + e.getMessage());
        }
    }
}

