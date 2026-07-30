package com.realarbird.spotimc.social;

import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.network.SpotiMCSongPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player song states on the client and handles broadcasting current player track info over network.
 */
public class ClientSongTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/Social");

    public record PlayerSongInfo(
            UUID uuid,
            String playerName,
            String trackName,
            String artistName,
            boolean isPlaying
    ) {}

    private static final Map<UUID, PlayerSongInfo> PLAYER_SONGS = new ConcurrentHashMap<>();
    private static long lastBroadcastTime = 0;

    /**
     * Updates song info stored for a player.
     */
    public static void updateSong(SpotiMCSongPayload payload) {
        if (payload == null || payload.playerUuid() == null) return;

        if (payload.isPlaying() && payload.trackName() != null && !payload.trackName().isEmpty()) {
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
     * Periodically sends local player's song update packet to server/peers.
     * Respects user privacy settings (shareMyListeningStats).
     */
    public static void tickBroadcast(String trackName, String artistName, boolean isPlaying) {
        long now = System.currentTimeMillis();
        if (now - lastBroadcastTime < 2500) {
            return;
        }
        lastBroadcastTime = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        UUID localUuid = mc.player.getUUID();
        String localName = mc.player.getName().getString();

        SpotiMCConfig config = SpotiMCConfig.getInstance();
        boolean share = config.shareMyListeningStats;

        SpotiMCSongPayload payload = new SpotiMCSongPayload(
                localUuid,
                localName,
                share && trackName != null ? trackName : "",
                share && artistName != null ? artistName : "",
                share && isPlaying
        );

        // Update locally for singleplayer/local view
        updateSong(payload);

        // Send to server if connected
        if (ClientPlayNetworking.canSend(SpotiMCSongPayload.TYPE)) {
            ClientPlayNetworking.send(payload);
        }
    }
}
