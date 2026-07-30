package com.realarbird.spotimc;

import com.realarbird.spotimc.network.SpotiMCSongPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for SpotiMC.
 * Handles payload registry and server-side packet broadcasting for the social feature.
 */
public class SpotiMCMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SpotiMC/Common");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SpotiMC common networking...");

        // Register custom payload for C2S and S2C play channels
        PayloadTypeRegistry.serverboundPlay().register(SpotiMCSongPayload.TYPE, SpotiMCSongPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpotiMCSongPayload.TYPE, SpotiMCSongPayload.CODEC);

        // Server packet receiver: broadcast song updates to all other online players
        ServerPlayNetworking.registerGlobalReceiver(SpotiMCSongPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                // Ignore the identity supplied by the client so a player can update
                // only their own overhead status.
                SpotiMCSongPayload update = new SpotiMCSongPayload(
                        sender.getUUID(),
                        sender.getName().getString(),
                        payload.trackName(),
                        payload.artistName(),
                        payload.isPlaying()
                );
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (player != sender && ServerPlayNetworking.canSend(player, SpotiMCSongPayload.TYPE)) {
                        ServerPlayNetworking.send(player, update);
                    }
                }
            });
        });
    }
}
