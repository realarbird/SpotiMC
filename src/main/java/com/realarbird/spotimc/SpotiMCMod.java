package com.realarbird.spotimc;

import com.realarbird.spotimc.network.SpotiMCSongPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common entrypoint for SpotiMC.
 * Handles payload registry, server-side song state persistence, and server-side packet broadcasting for the social feature.
 */
public class SpotiMCMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/Common");

    // Server-side cache of active song statuses for all connected players
    private static final Map<UUID, SpotiMCSongPayload> ACTIVE_PLAYER_SONGS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        System.out.println("[SpotiMC] SpotiMCMod.onInitialize() ENTRY");
        try {
            LOGGER.info("Initializing SpotiMC common networking...");

            // Register custom payload for C2S and S2C play channels
            PayloadTypeRegistry.serverboundPlay().register(SpotiMCSongPayload.TYPE, SpotiMCSongPayload.CODEC);
            PayloadTypeRegistry.clientboundPlay().register(SpotiMCSongPayload.TYPE, SpotiMCSongPayload.CODEC);

            // Server packet receiver: record song update and broadcast to all other online players
            ServerPlayNetworking.registerGlobalReceiver(SpotiMCSongPayload.TYPE, (payload, context) -> {
                ServerPlayer sender = context.player();
                context.server().execute(() -> {
                    // Create server-validated payload using authenticated sender info
                    SpotiMCSongPayload update = new SpotiMCSongPayload(
                            sender.getUUID(),
                            sender.getName().getString(),
                            payload.trackName(),
                            payload.artistName(),
                            payload.isPlaying()
                    );

                    // Update server state cache
                    if (update.isPlaying() && update.trackName() != null && !update.trackName().trim().isEmpty()) {
                        ACTIVE_PLAYER_SONGS.put(sender.getUUID(), update);
                    } else {
                        ACTIVE_PLAYER_SONGS.remove(sender.getUUID());
                    }

                    // Broadcast to all other online players
                    for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                        if (player != sender && ServerPlayNetworking.canSend(player, SpotiMCSongPayload.TYPE)) {
                            ServerPlayNetworking.send(player, update);
                        }
                    }
                });
            });

            // Player JOIN event: sync all currently active player song statuses to the joining player
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                ServerPlayer player = handler.getPlayer();
                server.execute(() -> {
                    for (SpotiMCSongPayload activeSong : ACTIVE_PLAYER_SONGS.values()) {
                        if (activeSong.playerUuid() != null && !activeSong.playerUuid().equals(player.getUUID())) {
                            if (ServerPlayNetworking.canSend(player, SpotiMCSongPayload.TYPE)) {
                                ServerPlayNetworking.send(player, activeSong);
                            }
                        }
                    }
                });
            });

            // Player DISCONNECT event: clean up server cache and inform all remaining players to remove nametag
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
                ServerPlayer player = handler.getPlayer();
                UUID uuid = player.getUUID();
                server.execute(() -> {
                    ACTIVE_PLAYER_SONGS.remove(uuid);
                    SpotiMCSongPayload clearPayload = new SpotiMCSongPayload(
                            uuid,
                            player.getName().getString(),
                            "",
                            "",
                            false
                    );
                    for (ServerPlayer remaining : server.getPlayerList().getPlayers()) {
                        if (remaining != player && ServerPlayNetworking.canSend(remaining, SpotiMCSongPayload.TYPE)) {
                            ServerPlayNetworking.send(remaining, clearPayload);
                        }
                    }
                });
            });

            LOGGER.info("SpotiMC common networking initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("[SpotiMC] FATAL: SpotiMCMod.onInitialize() failed!", e);
            System.err.println("[SpotiMC] FATAL: SpotiMCMod.onInitialize() failed: " + e);
            e.printStackTrace(System.err);
        }
    }
}

